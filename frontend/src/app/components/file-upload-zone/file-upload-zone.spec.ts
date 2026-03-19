import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FileUploadZone } from './file-upload-zone';

describe('FileUploadZone', () => {
  let component: FileUploadZone;
  let fixture: ComponentFixture<FileUploadZone>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FileUploadZone],
    }).compileComponents();

    fixture = TestBed.createComponent(FileUploadZone);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
