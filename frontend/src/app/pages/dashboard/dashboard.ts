import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AuthService } from '../../services/auth.service';
import { OcrService, FinancialAnalysis, InsightRecord } from '../../services/ocr.service';

export interface OcrResult {
  fileName: string;
  analysis: FinancialAnalysis;
}

@Component({
  selector: 'app-dashboard',
  imports: [],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit {
  private authService = inject(AuthService);
  private ocrService = inject(OcrService);
  private router = inject(Router);

  // ─── Estado do upload ────────────────────────────────────────────────────
  selectedFiles = signal<File[]>([]);
  isDragging = signal(false);

  // ─── Estado da análise ───────────────────────────────────────────────────
  isAnalyzing = signal(false);

  // null = nenhuma análise feita ainda
  results = signal<OcrResult[] | null>(null);

  // Mensagem de erro caso algum arquivo falhe
  errorMessage = signal<string | null>(null);

  // ─── Histórico ───────────────────────────────────────────────────────────
  history = signal<InsightRecord[]>([]);
  isLoadingHistory = signal(false);

  // id do card expandido no histórico — null = nenhum expandido
  expandedId = signal<number | null>(null);

  // Chamado automaticamente pelo Angular ao criar o componente.
  // Carrega o histórico persistido para que os dados sobrevivam ao F5.
  ngOnInit() {
    this.loadHistory();
  }

  private loadHistory() {
    this.isLoadingHistory.set(true);
    this.ocrService
      .getInsights()
      .pipe(finalize(() => this.isLoadingHistory.set(false)))
      .subscribe({
        next: (items) => this.history.set(items),
        error: () => this.history.set([]),
      });
  }

  // ─── Logout ──────────────────────────────────────────────────────────────
  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  // ─── Drag & Drop ─────────────────────────────────────────────────────────

  onDragOver(event: DragEvent) {
    event.preventDefault();
    this.isDragging.set(true);
  }

  onDragLeave() {
    this.isDragging.set(false);
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    this.isDragging.set(false);
    const dropped = event.dataTransfer?.files;
    if (dropped && dropped.length > 0) {
      this.addFiles(Array.from(dropped));
    }
  }

  // ─── Input de arquivo (clique) ───────────────────────────────────────────

  onFileInputChange(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.addFiles(Array.from(input.files));
      input.value = '';
    }
  }

  private addFiles(files: File[]) {
    const current = this.selectedFiles();
    const currentNames = new Set(current.map((f) => f.name));
    const newFiles = files.filter((f) => !currentNames.has(f.name));
    this.selectedFiles.set([...current, ...newFiles]);
  }

  removeFile(index: number) {
    const updated = this.selectedFiles().filter((_, i) => i !== index);
    this.selectedFiles.set(updated);
    if (updated.length === 0) {
      this.results.set(null);
      this.errorMessage.set(null);
    }
  }

  // ─── Análise ─────────────────────────────────────────────────────────────

  analyzeFiles() {
    const files = this.selectedFiles();
    if (files.length === 0) return;

    this.isAnalyzing.set(true);
    this.results.set(null);
    this.errorMessage.set(null);

    // forkJoin dispara todas as requisições em paralelo e aguarda todas terminarem.
    // analyzeDocument faz OCR + mascaramento de PII + análise financeira via IA.
    const requests = files.map((file) => this.ocrService.analyzeDocument(file));

    forkJoin(requests)
      .pipe(finalize(() => this.isAnalyzing.set(false)))
      .subscribe({
        next: (analyses) => {
          const results: OcrResult[] = files.map((file, i) => ({
            fileName: file.name,
            analysis: analyses[i],
          }));
          this.results.set(results);
          // Atualiza o histórico para incluir a análise que acabou de ser salva no backend
          this.loadHistory();
        },
        error: (err) => {
          // err.error pode ser: string, objeto JSON, ou null
          // Sem a guarda typeof, um objeto cai direto no template e vira "[object Object]"
          const msg =
            err.error?.message ??
            (typeof err.error === 'string' ? err.error : null) ??
            'Erro ao processar o arquivo.';
          this.errorMessage.set(msg);
        },
      });
  }

  // ─── Histórico: expandir / recolher ──────────────────────────────────────

  toggleExpand(id: number) {
    this.expandedId.set(this.expandedId() === id ? null : id);
  }

  // Remove o item do backend e atualiza a lista local sem refetch.
  deleteHistoryItem(id: number) {
    this.ocrService.deleteInsight(id).subscribe({
      next: () => {
        this.history.set(this.history().filter((item) => item.id !== id));
        if (this.expandedId() === id) this.expandedId.set(null);
      },
    });
  }

  // ─── Utilitários de template ─────────────────────────────────────────────

  formatFileSize(bytes: number): string {
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  getFileExtension(name: string): string {
    return name.split('.').pop()?.toUpperCase() ?? 'FILE';
  }

  formatDate(isoDate: string): string {
    return new Date(isoDate).toLocaleString('pt-BR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}