from pathlib import Path

p = Path(r"C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\java\com\example\kpkn\screens\workout\WorkoutSetPager.kt")
text = p.read_text(encoding="utf-8")
start = text.find("@Composable\nprivate fun ActivityCloudPill(")
if start < 0:
    start = text.find("@Composable\r\nprivate fun ActivityCloudPill(")
end = text.find("@Composable\ninternal fun WorkoutSetPager(", start)
if end < 0:
    end = text.find("@Composable\r\ninternal fun WorkoutSetPager(", start)
if start < 0 or end < 0:
    raise SystemExit(f"markers not found start={start} end={end}")

nl = "\r\n" if "\r\n" in text[start:start+40] else "\n"
new = """@Composable
private fun ActivityCloudPill(
    area: ActivityCloudArea,
    segmentWidth: Dp,
    accent: Color,
) {
    val connectorColor = accent.copy(alpha = 0.30f)
    val cloudLabelWidth = when (area) {
        ActivityCloudArea.PREPARATION -> (segmentWidth - 8.dp)
            .coerceAtLeast(72.dp)
            .coerceAtMost(148.dp)
        ActivityCloudArea.EFFECTIVE_SERIES -> (segmentWidth - 8.dp)
            .coerceAtLeast(100.dp)
            .coerceAtMost(168.dp)
        ActivityCloudArea.SUPERSERIE -> (segmentWidth - 8.dp)
            .coerceAtLeast(88.dp)
            .coerceAtMost(156.dp)
    }
    val cloudLabelFontSize = if (segmentWidth < 100.dp) 8.5.sp else 10.sp
    Box(
        modifier = Modifier
            .width(segmentWidth)
            .height(STEPPER_CLOUD_HEIGHT),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .background(connectorColor),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Surface(
                modifier = Modifier.requiredWidth(cloudLabelWidth),
                shape = WorkoutUiTokens.ChipShape,
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Text(
                    text = area.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = cloudLabelFontSize),
                    fontWeight = FontWeight.Black,
                    color = accent.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(connectorColor),
            )
        }
    }
}

"""
if nl == "\r\n":
    new = new.replace("\n", "\r\n")
text = text[:start] + new + text[end:]
# ensure TextAlign import
if "text.style.TextAlign" not in text:
    text = text.replace(
        "import androidx.compose.ui.text.style.TextOverflow",
        "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.text.style.TextOverflow",
    )
p.write_text(text, encoding="utf-8")
print("pill ok")
