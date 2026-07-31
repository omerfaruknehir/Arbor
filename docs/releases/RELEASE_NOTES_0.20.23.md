# Arbor 0.20.23

## Provider models are a real multi-select

After Arbor fetches a provider's model catalog, every model is selected by default. You can search, clear, select all, or choose any subset. Only that subset is stored for the provider.

## Cloud backups without forced passwords

**Backup & transfer** uses Android's document picker, so any installed cloud document provider can be the destination. Password encryption is optional. Leaving it blank is permitted and shows a clear exposure warning; credentials and OAuth sessions are never included.

## Portable chat sharing

Chats can be shared as `.arborchat` files. Visible messages and attachments are the safe default, while reasoning, tool traces, custom prompts, and request metadata are separate opt-in controls. Arbor previews incoming files before importing them as non-destructive copies and can immediately continue the imported chat.
