import {Component, EventEmitter, HostBinding, Input, Output} from "@angular/core";
import {ToastTypes} from "./toast.model";

@Component({
  selector: 'sc-toast',
  template: `
    <ng-content></ng-content>
    <button *ngIf="closeable"
            (click)="close.next()" 
            type="button" 
            class="close cursor-pointer" 
            data-dismiss="alert" 
            aria-label="Close">
      <span aria-hidden="true">&times;</span>
    </button>
  `,
  styles: [`
    :host() {
      display: flex;
      flex-direction: row;
    }
  `]
})
export class ScToastComponent {
  @Input() message = '';
  @Input('toast-type') type: ToastTypes = 'info';
  @Input() closeable = false;

  @Output() close = new EventEmitter<any>();

  @HostBinding('class')
  get hostBindingClass() {
    return 'alert shade';
  }

  @HostBinding('class.alert-success')
  get hostBindingClassSuccess() {
    return this.type === 'success'
  }

  @HostBinding('class.alert-warning')
  get hostBindingClassWarning() {
    return this.type === 'warning'
  }

  @HostBinding('class.alert-info')
  get hostBindingClassInfo() {
    return this.type === 'info'
  }

  @HostBinding('class.alert-danger')
  get hostBindingClassDanger() {
    return this.type === 'danger'
  }

  @HostBinding('class.alert-dismissible')
  get hostBindingClassDismissible() {
    return this.closeable;
  }


}
