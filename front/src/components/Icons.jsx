import logoImage from '../assets/images/logo.png';

export function ChevronsLeftIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M15 6l-6 6 6 6" />
    </svg>
  );
}

export function DashboardIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" {...props}>
      <circle cx="12" cy="12" r="9" />
      <ellipse cx="12" cy="12" rx="9" ry="3.6" />
      <circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none" />
    </svg>
  );
}

export function PersonIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" {...props}>
      <circle cx="12" cy="9" r="4.2" />
      <path d="M4.5 20c1.3-3.7 4-5.6 7.5-5.6s6.2 1.9 7.5 5.6" />
    </svg>
  );
}

export function ChatIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" {...props}>
      <path d="M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v7A2.5 2.5 0 0 1 17.5 16H10l-4.2 3.4a.5.5 0 0 1-.8-.4V16h-.5A2.5 2.5 0 0 1 2 13.5v0" />
      <circle cx="17" cy="6" r="1.4" fill="currentColor" stroke="none" />
    </svg>
  );
}

export function PlusIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" {...props}>
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

export function ChevronDownIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}

export function SparkleIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" {...props}>
      <path d="M12 3l1.8 4.6L18 9l-4.2 1.6L12 15l-1.8-4.4L6 9l4.2-1.4z" />
    </svg>
  );
}

export function WarnIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" {...props}>
      <path d="M12 3l9 16H3z" />
      <path d="M12 10v4" />
      <circle cx="12" cy="17" r="0.6" fill="currentColor" />
    </svg>
  );
}

export function CloseIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" {...props}>
      <path d="M6 6l12 12M18 6L6 18" />
    </svg>
  );
}

export function SearchIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" {...props}>
      <circle cx="11" cy="11" r="7" />
      <path d="M21 21l-4.3-4.3" />
    </svg>
  );
}

export function UploadIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" {...props}>
      <path d="M12 16V4M12 4l-4 4M12 4l4 4" />
      <path d="M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
    </svg>
  );
}

export function CheckIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" {...props}>
      <path d="M20 6L9 17l-5-5" />
    </svg>
  );
}

export function QuoteIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" {...props}>
      <path d="M7 8c-2 1-3 3-3 5.5S6 18 8 18" />
      <path d="M15 8c-2 1-3 3-3 5.5s2 4.5 4 4.5" />
    </svg>
  );
}

export function InfoIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5" />
      <circle cx="12" cy="8" r="0.6" fill="currentColor" />
    </svg>
  );
}

export function SendIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" {...props}>
      <path d="M3 11.5L20.5 3.8a.6.6 0 0 1 .8.76l-5.2 16.6a.6.6 0 0 1-1.08.13l-3.5-5.7-5.7-3.5a.6.6 0 0 1 .18-1.09z" />
    </svg>
  );
}

export function KakaoIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" {...props}>
      <path d="M12 3.6C6.5 3.6 2 7.2 2 11.6c0 2.8 1.9 5.3 4.7 6.8-.2.8-.9 3-.9 3.4 0 .3.2.4.4.3.2 0 2.9-1.9 4-2.7.6.1 1.2.2 1.8.2 5.5 0 10-3.6 10-8s-4.5-8-10-8z" />
    </svg>
  );
}

export function LogoMark({ size = 34 }) {
  const displaySize = Math.round(size * 2.25);

  return (
    <img
      src={logoImage}
      alt=""
      width={displaySize}
      height={displaySize}
      style={{
        display: 'block',
        flex: 'none',
        aspectRatio: '1 / 1',
        objectFit: 'contain',
      }}
    />
  );
}

export function PixelInfoIcon(props) {
  return (
    <svg viewBox="0 0 12 12" fill="currentColor" shapeRendering="crispEdges" {...props}>
      <rect x="2" y="0" width="8" height="1" />
      <rect x="2" y="11" width="8" height="1" />
      <rect x="0" y="2" width="1" height="8" />
      <rect x="11" y="2" width="1" height="8" />
      <rect x="1" y="1" width="1" height="1" />
      <rect x="10" y="1" width="1" height="1" />
      <rect x="1" y="10" width="1" height="1" />
      <rect x="10" y="10" width="1" height="1" />
      <rect x="5" y="3" width="2" height="2" />
      <rect x="5" y="6" width="2" height="3" />
    </svg>
  );
}

export function GuideIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" {...props}>
      <path d="M12 5.5C10.4 4.2 8.2 3.6 5.5 3.6c-.6 0-1 .4-1 1v13c0 .6.4 1 1 1 2.7 0 4.9.6 6.5 1.9 1.6-1.3 3.8-1.9 6.5-1.9.6 0 1-.4 1-1v-13c0-.6-.4-1-1-1-2.7 0-4.9.6-6.5 1.9z" />
      <path d="M12 5.5v15" />
    </svg>
  );
}

export function PixelPlayIcon(props) {
  return (
    <svg viewBox="0 0 12 12" fill="currentColor" shapeRendering="crispEdges" {...props}>
      <rect x="2" y="2" width="2" height="8" />
      <rect x="4" y="3" width="2" height="6" />
      <rect x="6" y="4" width="2" height="4" />
      <rect x="8" y="5" width="2" height="2" />
    </svg>
  );
}
