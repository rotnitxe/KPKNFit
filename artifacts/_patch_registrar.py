from pathlib import Path

p = Path(
    r"C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\java\com\example\kpkn\screens\workout\components\SetExecutionCard.kt"
)
text = p.read_text(encoding="utf-8")
start = text.find("                    if (isActivePage) {\n                        Button(\n                            onClick = { recordActionHolder.action?.invoke() },")
if start < 0:
    start = text.find("                    if (isActivePage) {\r\n                        Button(\r\n                            onClick = { recordActionHolder.action?.invoke() },")
if start < 0:
    raise SystemExit("start not found")
# find end of this if block: closing "                    }" after Registrar button
marker = 'text = "Registrar"'
mi = text.find(marker, start)
if mi < 0:
    raise SystemExit("registrar marker not found")
# after the Text and Icon block, find "                    }\n                }"
end_search = text.find("\n                    }\n                }", mi)
if end_search < 0:
    end_search = text.find("\r\n                    }\r\n                }", mi)
    if end_search < 0:
        raise SystemExit("end not found")
    end = end_search + len("\r\n                    }")
    nl = "\r\n"
else:
    end = end_search + len("\n                    }")
    nl = "\n"

new = f"""                    // Always render Registrar so peeks keep the same card height as the
                    // settled page (no layout jump on swipe). Only the active page arms it.
                    Button(
                        onClick = {{ recordActionHolder.action?.invoke() }},
                        enabled = isActivePage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = WorkoutUiTokens.MinTouchTarget),
                        shape = WorkoutUiTokens.InnerCardShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = sessionAccentColor,
                            contentColor = com.example.kpkn.screens.sessioneditor.contentOn(sessionAccentColor),
                            disabledContainerColor = sessionAccentColor.copy(alpha = 0.55f),
                            disabledContentColor = com.example.kpkn.screens.sessioneditor.contentOn(sessionAccentColor)
                                .copy(alpha = 0.72f),
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {{
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier.width(6.dp))
                        Text(
                            text = "Registrar",
                            fontWeight = FontWeight.Black,
                        )
                    }}"""
if nl == "\r\n":
    new = new.replace("\n", "\r\n")
text = text[:start] + new + text[end:]
p.write_text(text, encoding="utf-8")
print("ok", start, end)
