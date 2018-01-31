import {Component, Input, OnInit} from '@angular/core';
import {TestCaseStepResult} from "../../../sweetest-components/services/access/model/test-result.interface";
import {rmHeadSlash} from "../../../sweetest-components/services/access/file.service";
import {resultStateMap} from "./result-state-map.const";

@Component({
  selector: 'sa-report-steps',
  template: `
    <ng-container>
      <li class="list-group-item pointer-cursor d-flex flex-row" (click)="toggleCollapse()">
        <sc-icon [icon]="step?.testActions?.length ? 'fa-caret-right' : ''" [fixedWidth]="true" [rotate]="collapsed ? 90 : 0"></sc-icon>
        <ng-container *ngIf="step.exception">
          <sc-icon iconClass="text-danger" icon="fa-exclamation-triangle"></sc-icon>
        </ng-container>
        <strong>{{step.name}}</strong>
        <ng-template #noException>
          <span class=" pl-1">{{step.testActions?.length}} actions </span>
          <span class="text-muted  pl-1" *ngIf="step.stopDate">
            <sc-icon icon="fa-clock-o"></sc-icon>
            {{((step.stopDate | dateDiff:step.startDate) / 1000) | number}} sec
          </span>
          <span class="m-1 badge badge-warning" *ngIf="step.state === 'WARNING'">
            Warningtime of <strong>{{step.warningTime}}</strong> sec exceeded
          </span>
        </ng-template>
        <ng-container *ngIf="step.exception; else noException">
          <div class="exception-title text-danger pl-1">{{step.exception.detailMessage}}</div>
        </ng-container>
      </li>
      <li class="list-group-item m-0 p-0 border-0 d-flex flex-row bg-light" style="height: 3px; background-color: silver">
        <div [ngStyle]="{'flex-grow': durationOffsetPercent}"></div>
        <div [ngStyle]="{'flex-grow': durationPercent}" class="bg-success"></div>
      </li>
      <sc-collapse [show]="collapsed">
        <ng-container *ngIf="step.exception; else actions">
          <li class="list-group-item d-flex flex-column">
            <strong>{{step.exception.detailMessage}}</strong>
            <a [href]="'api/files?path=' + rmHeadSlash(step.exception.screenshot)" target="_blank" class="text-center mt-1 mb-1">
              <img scAuthenticated [src]="'api/files?path=' + rmHeadSlash(step.exception.screenshot)" width="250"/>
            </a>
            <div>
              <button class="btn btn-link" (click)="showStacktrace = !showStacktrace">Show Stacktrace</button>
              <button class="btn btn-link"
                      *ngIf="showStacktrace"
                      (click)="toClipBoard(stackTraceArea)">Copy stack trace to clipboard
              </button>
            </div> 
            <textarea #stackTraceArea 
                      [disabled]="true" 
                      rows="20"
                      [hidden]="!showStacktrace"
            >{{step.exception.stackTrace}}</textarea>
          </li>
        </ng-container>
        <ng-template #actions>
          <sa-action *ngFor="let action of step.testActions" [action]="action"></sa-action>
        </ng-template>
      </sc-collapse>
    </ng-container>
  `,
  styles: [`
    .list-group-item {
      word-wrap: break-word;
    }

    pre {
      background-color: #EEEEEE;
      font-family: Consolas, Menlo, Monaco, Lucida Console, Liberation Mono, DejaVu Sans Mono, Bitstream Vera Sans Mono, Courier New, monospace, serif;
      margin-bottom: 10px;
      max-height: 600px;
      overflow: auto;
      padding: 5px;
      width: auto;
    }
    
    .exception-title {
      flex-grow: 1;
      text-overflow: ellipsis;
      overflow: hidden;
      white-space: nowrap;
    }
    
    textarea {
      border: 0;
    }
  `]
})

export class SaReportStepsComponent implements OnInit {

  rmHeadSlash = rmHeadSlash;

  @Input() step: TestCaseStepResult;
  @Input() durationPercent: number;
  @Input() durationOffsetPercent: number;

  collapsed = false;

  showStacktrace = false;

  toggleCollapse() {
    this.collapsed = !this.collapsed;
  }

  constructor() {
  }

  ngOnInit() {
  }

  toClipBoard(textArea: HTMLTextAreaElement) {
    const pd = textArea.disabled;
    textArea.disabled = false;
    textArea.focus();
    textArea.setSelectionRange(0, textArea.value.length);
    const r = document.execCommand('copy');
    textArea.setSelectionRange(0, 0);
    textArea.disabled = pd;
  }

  backgroundState(state: string) {
    console.log(state);

    return state === 'OK' ? 'bg-default' :  `bg-${resultStateMap[state]}`;
  }
}