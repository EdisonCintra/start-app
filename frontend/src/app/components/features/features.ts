import { Component } from '@angular/core';

@Component({
  selector: 'app-features',
  imports: [],
  templateUrl: './features.html',
  styleUrl: './features.css',
})
export class Features {
  features = [
    {
      title: 'OCR + Extração de Texto',
      desc: 'Lê PDFs com texto embutido ou escaneados. Para imagens, usa Tesseract para extrair cada linha da fatura.',
      icon: 'zap',
    },
    {
      title: 'Privacidade Garantida',
      desc: 'CPF, CNPJ, RG, e-mails e telefones são mascarados automaticamente antes de qualquer envio à IA.',
      icon: 'shield',
    },
    {
      title: 'Análise Financeira com IA',
      desc: 'Classifica entradas e saídas por categoria, identifica padrões de gasto e gera conselhos personalizados.',
      icon: 'bar',
    },
  ];
}
