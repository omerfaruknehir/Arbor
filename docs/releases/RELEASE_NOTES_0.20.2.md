# Xylune 0.20.2

Xylune 0.20.2 makes the Thinking, Search, and Tools pills easier to scroll. Their horizontal gesture viewport now spans the full composer width instead of being inset around the visible pills, while the first pill keeps the requested 36 dp alignment.

The larger gesture surface stays confined to the pill row so it does not steal touch handling from the message field or vertical chat scrolling.

Working is quieter and substantially smaller. Completed and failed steps stay collapsed, failed script runs show one useful error line plus Retry, and raw code, output, source paths, and copyable diagnostics appear only when Tool diagnostics is enabled inside Developer options.

Developer options now lives at the bottom of About Xylune instead of occupying a main Settings category. About Xylune now identifies the creator and GitHub project, links to source and issues, and reports the installed version, build, package, Android support, runtime API, ABI, and privacy model.

The release keeps the `app.xylune.chat.debug` package, debug signer, Room schema, migrations, existing chats, credentials, OAuth sessions, workspaces, and attachments compatible.
