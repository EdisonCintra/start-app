import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ResultsPreview } from './results-preview';

describe('ResultsPreview', () => {
  let component: ResultsPreview;
  let fixture: ComponentFixture<ResultsPreview>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResultsPreview],
    }).compileComponents();

    fixture = TestBed.createComponent(ResultsPreview);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
