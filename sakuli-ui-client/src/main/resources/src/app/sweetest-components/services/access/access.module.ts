import {NgModule} from "@angular/core";
import {FileService} from "./file.service";
import {TestService} from "./test.service";
import {HttpClientModule} from "@angular/common/http";
import {ScAuthModule} from "./auth/sc-auth.module";
import {StompConfig, StompService} from "@stomp/ng2-stompjs";
import {stompConfig} from "./stomp.config";
import {AppInfoService} from "./app-info.service";

export const providers = [
  TestService,
  FileService,
  StompService,
  AppInfoService
];

@NgModule({
  imports: [
    HttpClientModule,
    ScAuthModule
  ],
  providers: [
    ...providers,
    {
      provide: StompConfig,
      useValue: stompConfig
    }
  ],
  exports: [
    ScAuthModule
  ]
})
export class ScAccessModule {
}
