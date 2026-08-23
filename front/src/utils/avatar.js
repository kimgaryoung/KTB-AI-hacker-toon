const GRADIENTS = [
  'radial-gradient(circle at 32% 28%, #ffe9c9, #e2a0c9 60%, #a5629a)',
  'radial-gradient(circle at 32% 28%, #efe3ff, #a595e8 60%, #6f5cc4)',
  'radial-gradient(circle at 32% 28%, #d9fff0, #7fd9b6 60%, #3f9d7e)',
  'radial-gradient(circle at 32% 28%, #ffe3df, #e2896f 60%, #a5533c)',
  'radial-gradient(circle at 32% 28%, #fff3d9, #f0b56c 60%, #b87c34)',
  'radial-gradient(circle at 32% 28%, #e6f0ff, #8fb3f0 60%, #4d6fb0)',
];

// Stable per-relationship gradient derived from its UUID, so the same
// person always gets the same avatar color across screens without the
// backend needing to store one.
export function avatarGradientFor(id) {
  let hash = 0;
  for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
  return GRADIENTS[hash % GRADIENTS.length];
}

export function initialsOf(name) {
  return name.slice(-2);
}
