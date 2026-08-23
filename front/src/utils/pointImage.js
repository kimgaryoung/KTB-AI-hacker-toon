import point100 from '../assets/images/point100.png';
import point80 from '../assets/images/point80.png';
import point60 from '../assets/images/point60.png';
import point40 from '../assets/images/point40.png';
import point20 from '../assets/images/point20.png';

const POINT_IMAGES = [
  { minimum: 80, image: point100 },
  { minimum: 60, image: point80 },
  { minimum: 40, image: point60 },
  { minimum: 20, image: point40 },
  { minimum: 0, image: point20 },
];

export function pointImageFor(score) {
  const numericScore = Number(score);
  if (!Number.isFinite(numericScore)) return null;
  const clampedScore = Math.max(0, Math.min(100, numericScore));
  return POINT_IMAGES.find(({ minimum }) => clampedScore >= minimum)?.image ?? point20;
}
