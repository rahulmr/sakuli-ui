package org.sweetest.platform.server.service.test.execution.strategy;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.command.AttachContainerResultCallback;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.SocketUtils;
import org.sweetest.platform.server.api.common.Observer;
import org.sweetest.platform.server.api.test.TestRunInfo;
import org.sweetest.platform.server.api.test.execution.strategy.AbstractTestExecutionStrategy;
import org.sweetest.platform.server.api.test.execution.strategy.TestExecutionEvent;
import org.sweetest.platform.server.api.test.execution.strategy.TestExecutionSubject;
import org.sweetest.platform.server.api.test.execution.strategy.events.TestExecutionErrorEvent;
import org.sweetest.platform.server.api.test.execution.strategy.events.TestExecutionLogEvent;

import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

import static com.github.dockerjava.api.model.Ports.Binding.bindPort;
import static org.sweetest.platform.server.ApplicationConfig.DOCKER_CONTAINER_SAKULI_UI_USER;
import static org.sweetest.platform.server.ApplicationConfig.SAKULI_NETWORK_NAME;

public abstract class AbstractContainerTestExecutionStrategy<T> extends AbstractTestExecutionStrategy<T> {
    private static final Logger log = LoggerFactory.getLogger(AbstractContainerTestExecutionStrategy.class);

    @Autowired
    protected DockerClient dockerClient;
    @Autowired
    @Qualifier("rootDirectory")
    protected String rootDirectory;
    @Value("${docker.userid:1000}")
    protected String dockerUserId;

    protected TestExecutionSubject subject;
    protected CreateContainerResponse containerReference;
    protected String executionId;
    protected AttachContainerResultCallback callback;
    protected ExposedPort vncPort;
    protected ExposedPort vncWebPort;
    protected Ports ports;
    private Network sakuliNetwork;

    public AbstractContainerTestExecutionStrategy() {
        this.subject = new TestExecutionSubject();
    }

    void next(TestExecutionEvent event) {
        subject.next(event);
    }

    @Override
    public TestRunInfo execute(Observer<TestExecutionEvent> testExecutionEventObserver) {
        int availableVncPort = SocketUtils.findAvailableTcpPort(5901, 6900);
        int availableVncWebPort = SocketUtils.findAvailableTcpPort(6901);
        sakuliNetwork = resolveOrCreateSakuliNetwork();
        final String gateway = sakuliNetwork.getIpam().getConfig().stream().findFirst()
                .orElseGet(() -> {
                    next(new TestExecutionErrorEvent("No IPAM entry found in Sakuli Network", executionId, new RuntimeException()));
                    return new Network.Ipam.Config();
                })
                .getGateway();
        try {
            subject.subscribe(testExecutionEventObserver);
            executionId = createExecutionId();

            vncPort = ExposedPort.tcp(5901);
            vncWebPort = ExposedPort.tcp(6901);
            ports = new Ports();
            ports.bind(vncPort, bindPort(availableVncPort));
            ports.bind(vncWebPort, bindPort(availableVncWebPort));
            sakuliNetwork = resolveOrCreateSakuliNetwork();

            executeContainerStrategy();

        } catch (Exception e) {
            //TODO show TestExecutionErrorEvent on UI
            next(new TestExecutionErrorEvent(e.getMessage(), executionId, e));
        }
        final TestRunInfo tri = new TestRunInfo(
                gateway,
                availableVncPort,
                availableVncWebPort,
                executionId
        );
        tri.subscribe(invokeStopObserver(this));
        return tri;
    }

    /**
     * Have to implement for different container based strategies
     */
    protected abstract void executeContainerStrategy();

    /**
     * Creates a basic container configuration
     *
     * @param containerImageName
     * @return {@link CreateContainerCmd} for execution
     */
    protected CreateContainerCmd createContainerConfig(String containerImageName) {
        final String testSuitePath = "/" + Paths.get(testSuite.getRoot()).toString();

        //TODO Tim forward gateway to UI and proxy
        final String gateway = sakuliNetwork.getIpam().getConfig().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find any Network Ipam entry in SakuliNetwork"))
                .getGateway();
        log.info("use docker network: name={}, id={}, gateway={}", sakuliNetwork.getName(), sakuliNetwork.getId(), gateway);

        final CreateContainerCmd basicContainerCmd = dockerClient
                .createContainerCmd(containerImageName)
                .withExposedPorts(vncPort, vncWebPort)
                .withPortBindings(ports)
                .withPublishAllPorts(true)
                .withNetworkMode(sakuliNetwork.getId());


        if (System.getenv().containsKey(DOCKER_CONTAINER_SAKULI_UI_USER)) {
            log.info("Found {} in env. Preparing Docker-in-Docker", DOCKER_CONTAINER_SAKULI_UI_USER);
            //Sakuli UI is running in container itself -> start "docker-in-docker" container

            //ID of docker-ui-container is set on HOSTNAME
            final String uiContainerName = System.getenv("HOSTNAME");
            basicContainerCmd
                    .withUser(System.getenv(DOCKER_CONTAINER_SAKULI_UI_USER))
                    .withVolumesFrom(new VolumesFrom(uiContainerName, AccessMode.rw))
                    //use absolut path due to mounting under the same path
                    .withCmd("run", Paths.get(rootDirectory, testSuitePath).toString());
        } else {
            log.info("Preparing local volume");
            // This will mount a volume which looks like the local project path relative to the rootDirectory
            // This ensures that the testsuite has full access to all files in the workspace
            // the path to the root directory is omitted so that an user cannot this unnecessary and maybe insecure information
            final String workspacePath = ("/" + getWorkspace()).replace("//", "/");
            final Volume volume = new Volume(workspacePath);
            basicContainerCmd
                    .withUser(dockerUserId)
                    .withVolumes(volume)
                    .withBinds(new Bind(Paths.get(rootDirectory, workspacePath).toString(), volume))
                    .withCmd("run", testSuitePath);
        }
        log.info("Container configuration created for test suite '{}'", testSuitePath);
        return basicContainerCmd;
    }

    /**
     * Creates or Resolve the network for the internal container traffic.
     *
     * @return a docker network with the name {@link org.sweetest.platform.server.ApplicationConfig#SAKULI_NETWORK_NAME}
     */
    private Network resolveOrCreateSakuliNetwork() {
        return dockerClient.listNetworksCmd().exec().stream()
                .filter(d -> d.getName().equalsIgnoreCase(SAKULI_NETWORK_NAME))
                .findFirst().orElseGet(() -> {
                    dockerClient.createNetworkCmd()
                            .withName(SAKULI_NETWORK_NAME)
                            .withDriver("bridge")
                            .exec();
                    log.info("new docker network '{}'created!", SAKULI_NETWORK_NAME);
                    return resolveOrCreateSakuliNetwork();
                });
    }

    protected void startContainer() {
        log.info("Start pre-configured container for execution ID '{}'", executionId);
        dockerClient.eventsCmd().exec(new SakuliEventResultCallback(executionId, subject, dockerClient, containerReference));
        dockerClient.startContainerCmd(containerReference.getId())
                .exec();
        Optional.ofNullable(containerReference.getWarnings()).map(ReflectionToStringBuilder::toString)
                .ifPresent(w -> {
                    log.warn(w);
                    next(new TestExecutionEvent(TestExecutionEvent.TYPE_WARNING, w, executionId));
                });
    }

    protected void attachToContainer() {
        callback = dockerClient
                .logContainerCmd(containerReference.getId())
                .withStdOut(true)
                .withStdErr(true)
                .withTailAll()
                .withFollowStream(true)
                .exec(new AttachContainerResultCallback() {
                    @Override
                    public void onNext(Frame item) {
                        next(new TestExecutionLogEvent(
                                        executionId,
                                        new String(item.getPayload())
                                )
                        );
                        super.onNext(item);
                    }
                });

    }

    protected String createExecutionId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public void stop() {
        if (containerReference != null && containerReference.getId() != null) {
            log.info("Will stop containers " + containerReference.getId());
            try {
                if (callback != null) {
                    callback.close();
                }
                dockerClient
                        .killContainerCmd(containerReference.getId())
                        .withSignal("9")
                        .exec();
                containerReference = null;
            } catch (Exception e) {
                e.printStackTrace();
                next(new TestExecutionErrorEvent("Cannot stop containers " + containerReference.getId(), executionId, e));
            }
        } else {
            next(new TestExecutionLogEvent("no-container-event", "Cannot stop container: no container is started!"));
        }
    }
}
