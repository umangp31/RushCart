import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'inventory' },
  {
    path: 'inventory',
    loadComponent: () =>
      import('./features/inventory/inventory.component').then((m) => m.InventoryComponent),
  },
  {
    path: 'orders',
    loadComponent: () =>
      import('./features/orders/orders.component').then((m) => m.OrdersComponent),
  },
  {
    path: 'status',
    loadComponent: () =>
      import('./features/status/status.component').then((m) => m.StatusComponent),
  },
  {
    path: 'metrics',
    loadComponent: () =>
      import('./features/metrics/metrics.component').then((m) => m.MetricsComponent),
  },
  {
    path: 'guide',
    loadComponent: () => import('./features/guide/guide.component').then((m) => m.GuideComponent),
  },
  { path: '**', redirectTo: 'inventory' },
];
