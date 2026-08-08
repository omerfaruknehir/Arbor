# Xylune 0.24.19

- Source citations now keep their pills fixed and open a separate anchored preview card which grows and fades in from the tapped source instead of stretching the pill itself.
- Predictive Back edge contact no longer counts as an outside tap for popups and menus; dismissal is deferred until release and gestures that begin in a system Back edge are ignored by outside-tap handling.
- Large modal dialogs no longer use native outside-touch dismissal, preventing provider/editor dialogs from disappearing as soon as a Back gesture starts.
- When the keyboard is open inside a dialog, the first completed Back gesture belongs to the IME and keeps the surrounding dialog open; a later Back gesture can dismiss the dialog normally.
