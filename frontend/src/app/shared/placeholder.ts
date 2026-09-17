/**
 * Fallback art for products without an image: a two-tone gradient derived from the SKU,
 * so the same product always gets the same colours and neighbours look distinct.
 */
export function gradientFor(seed: string): string {
  let h = 2166136261;
  for (let i = 0; i < seed.length; i++) h = Math.imul(h ^ seed.charCodeAt(i), 16777619) >>> 0;
  const h1 = h % 360;
  const h2 = (h1 + 40 + ((h >>> 8) % 80)) % 360; // 40–120° away: related, not muddy
  const angle = 100 + ((h >>> 16) % 80);         // 100–180°
  return `linear-gradient(${angle}deg, hsl(${h1} 62% 58%) 0%, hsl(${h2} 58% 42%) 100%)`;
}
