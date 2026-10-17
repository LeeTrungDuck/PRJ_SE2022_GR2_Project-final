# Existing dashboard design

This records the existing CSS and the user's requested chat placement, rather than introducing a new visual direction.

- Canvas: `#0b1326`; surface/sidebar: `#131b2e`; inset: `#060e20`.
- Raised surface: `#222a3d`; border: `#343e54`.
- Text: `#f8fafc`; secondary: `#e2e8f0`; muted: `#cbd5e1`.
- Primary action: `#2563eb`; primary light: `#b4c5ff`.
- Success: `#4edea3`; error: `#ffb4ab`; warning: `#ffb95f`.
- Type: Inter/Segoe UI/sans-serif. Code and metadata use JetBrains Mono/Consolas.
- Desktop sidebar: 260px left; existing mobile sidebar is compact.
- Chat launcher: native details/summary with a local SVG icon, fixed bottom right. An iframe hosts the native form so submitting a question refreshes only the conversation.
- Chat panel: dark surface, clear heading, persistent composer and readable message text. Mobile width stays within the viewport. Controls have visible focus, labels and at least 44px primary targets.
- Chat sits below existing modal backdrop/dialog layers (40/41) and does not add decorative animation or external assets.
