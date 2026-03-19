import { Component } from '@angular/core';
import { Header } from '../../components/header/header';
import { Features } from '../../components/features/features';
import { FileUploadZone } from '../../components/file-upload-zone/file-upload-zone';
import { ResultsPreview } from '../../components/results-preview/results-preview';

@Component({
  selector: 'app-home',
  imports: [Header, Features, FileUploadZone, ResultsPreview],
  templateUrl: './home.html',
})
export class Home {}