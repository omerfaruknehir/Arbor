# Xylune website design QA

## Reference and capture

- Source of truth: `/workspace/scratch/7f3ddbdae871/generated_images/exec-ae937f5c-7131-490d-aa61-18c178a9f6c4.png`
- Source dimensions: 1487 × 1058 pixels.
- Implementation: `http://terminal.local:4173/?theme=dark`, captured in the cloud browser at a 1487 × 1058 CSS-pixel viewport with device scale factor 1.
- Comparison method: source and implementation were inspected at matching viewport dimensions. The QA wrapper used a visual scale only to fit the full comparison inside browser chrome; it did not change the page's CSS viewport or density.

## Interaction checks

- Desktop rail links and primary calls to action are reachable and legible.
- Mobile navigation opens, dismisses from the scrim, and does not remain behind the appearance dialog.
- Appearance dialog supports Dark, Light, and System on every visit.
- App theme appears only when Xylune supplies a valid color palette.
- A supplied app palette applies its exact primary, background, surface, rail, outline, and text colors.
- Theme choice is reflected in the URL, persisted for direct visits, and carried across internal links.
- Privacy, Terms, and Data Deletion pages reuse the same navigation, responsive shell, and appearance controls.

## Visual comparison history

1. The first pass included an extra eyebrow above the title, exposed App theme without a valid palette, and allowed the mobile menu to remain open behind the appearance dialog.
2. The eyebrow was removed, the hidden state was made authoritative, and opening appearance now dismisses the menu.
3. The final pass matches the selected V2 direction: a 292-pixel flat navigation rail, one focused content column, Material symbols, restrained green surfaces, clear cards, and no glass or decorative gradient treatment.

## Remaining differences

- P3, intentional: the Data Deletion description explains where data is actually held instead of promising that the maintainer can remove provider data.
- P3, intentional: the stock GitHub Pages theme credit was removed because the custom Xylune layout replaces that theme.
- Browser console: no errors from `terminal.local`; unrelated Chrome-extension metadata messages were excluded.

No P0, P1, or P2 visual or interaction issues remain.

final result: passed
