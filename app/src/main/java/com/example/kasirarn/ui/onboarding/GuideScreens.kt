package com.example.kasirarn.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kasirarn.R
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.HeroCard
import com.example.kasirarn.ui.components.IconTile
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.Spacing

/** Panduan penggunaan: semua tutorial langsung terlihat, dikelompokkan per bagian aplikasi. */
@Composable
fun GuideListScreen(onBack: () -> Unit, onStartTopic: (String) -> Unit, onStartTour: () -> Unit) {
    val vm = containerViewModel { OnboardingViewModel(it) }
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.background,
        topBar = { ArnTopBar(stringResource(R.string.userguide_title), onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val (heroBg, heroText) = com.example.kasirarn.ui.components.heroColors()
            // Tutorial interaktif: kartu sorotan di bagian atas.
            HeroCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(Spacing.xl),
                onClick = {
                    vm.setStage(OnboardingStage.TOUR)
                    onStartTour()
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.guide_interactive_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = heroText,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(R.string.guide_interactive_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = heroText.copy(alpha = 0.88f),
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Box(
                        Modifier.size(46.dp).background(heroText, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = stringResource(R.string.ob_start_tour), tint = heroBg)
                    }
                }
            }

            Text(
                stringResource(R.string.guide_topics_label),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md).semantics { heading() },
            )
            GuideSection.entries.forEach { section ->
                val topics = GuideTopics.filter { it.section == section }
                if (topics.isEmpty()) return@forEach
                Text(
                    stringResource(section.labelRes),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = scheme.primary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                topics.forEachIndexed { index, topic ->
                    val stepCount = GuideScripts[topic.id]?.size ?: 0
                    val (tileBg, tileFg) = when (index % 3) {
                        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
                        1 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
                    }
                    ArnCard(Modifier.fillMaxWidth(), onClick = { onStartTopic(topic.id) }, contentPadding = PaddingValues(Spacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(topic.icon, tileBg, tileFg, size = 52.dp, corner = 16.dp, iconSize = 26.dp)
                            Spacer(Modifier.width(Spacing.lg))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(topic.titleRes), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                                Text(
                                    stringResource(topic.summaryRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(R.string.guide_topic_steps, stepCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .background(scheme.primaryContainer, RoundedCornerShape(50))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurface)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
