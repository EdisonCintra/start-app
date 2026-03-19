import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

// Estrutura retornada pelo endpoint /ocr/analyze (análise financeira via IA)
export interface EntryItem {
  description: string;
  value: string;
  note: string | null;
}

export interface ExpenseItem {
  category: string;
  value: string;
  description: string;
}

export interface FinancialAnalysis {
  entries: {
    total: string | null;
    classNote: string | null;
    items: EntryItem[];
  } | null;
  expenses: {
    total: string | null;
    items: ExpenseItem[];
  } | null;
  insights: string[];
  advice: string[];
}

// Estrutura retornada pelo endpoint GET /insights
export interface InsightRecord {
  id: number;
  createdAt: string; // ISO 8601 — ex: "2026-03-16T14:32:00"
  analysis: FinancialAnalysis;
}

@Injectable({
  providedIn: 'root',
})
export class OcrService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/ocr`;

  // Envia um arquivo via multipart/form-data e recebe o texto extraído (mascarado).
  // responseType: 'text' porque o backend retorna String pura, não JSON.
  extractText(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post(this.apiUrl, formData, { responseType: 'text' });
  }

  // Envia o arquivo, aplica OCR + mascaramento de PII e retorna a análise financeira estruturada.
  analyzeDocument(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<FinancialAnalysis>(`${this.apiUrl}/analyze`, formData);
  }

  // Busca o histórico de análises do usuário autenticado, do mais recente ao mais antigo.
  getInsights() {
    return this.http.get<InsightRecord[]>(`${environment.apiUrl}/insights`);
  }

  // Remove um insight do histórico pelo id.
  deleteInsight(id: number) {
    return this.http.delete(`${environment.apiUrl}/insights/${id}`);
  }
}
