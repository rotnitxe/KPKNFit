    var userToggledRound by remember(naturalActiveRound) { mutableStateOf<Int?>(null) }
    val expandedRound = userToggledRound ?: naturalActiveRound

    val hasRounds = elements.any { it is TimelineElement.RoundBadge }
    val roundGroups = remember(elements) {
        val badges = elements.filterIsInstance<TimelineElement.RoundBadge>()
        badges.map { badge ->
            val sets = elements.filter {
                (it is TimelineElement.BilateralSet && it.roundIndex == badge.roundIndex) ||
                    (it is TimelineElement.UnilateralSet && it.roundIndex == badge.roundIndex)
            }
            Pair(badge, sets)
        }
    }

    // Line matches the shared stage height (instance or point-based min).