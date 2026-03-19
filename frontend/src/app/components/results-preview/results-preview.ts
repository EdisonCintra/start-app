import { Component } from '@angular/core';

@Component({
  selector: 'app-results-preview',
  imports: [],
  templateUrl: './results-preview.html',
  styleUrl: './results-preview.css',
})
export class ResultsPreview {
  entries = [
    { description: 'Pagamento em 10 MAR', value: 'R$ 194,85' },
  ];

  expenses = [
    { category: 'Alimentação', description: 'iFood - NuPay', value: 'R$ 45,90' },
    { category: 'Transporte', description: 'Uber Eats', value: 'R$ 28,50' },
    { category: 'Juros e Encargos', description: 'IOF parcelamento', value: 'R$ 12,40' },
  ];

  insights = [
    'Gastos com aplicativos de entrega representam 53% das saídas do período.',
  ];

  advice = [
    'Reduza pedidos por app — pequenas economias diárias têm grande impacto no orçamento mensal.',
  ];
}
