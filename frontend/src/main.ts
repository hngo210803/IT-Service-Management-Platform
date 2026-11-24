import {provideHttpClient} from '@angular/common/http';
import {provideZonelessChangeDetection} from '@angular/core';
import {bootstrapApplication} from '@angular/platform-browser';
import {ServiceDesk} from './service-desk';

bootstrapApplication(ServiceDesk, {providers: [provideHttpClient(), provideZonelessChangeDetection()]})
  .catch(error => console.error(error));
