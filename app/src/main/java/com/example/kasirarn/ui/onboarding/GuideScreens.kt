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
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.Spacing

/** Panduan penggunaan: semua tutorial langsung terlihat, dikelompokkan per bagian aplikasi. */
@Composable
fun GuideListScreen(onBack: () -> Unit, onOpenTopic: (String) -> Unit, onStartTour: () -> Unit) {
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
            // Tutorial interaktif: kartu berwarna di bagian atas.
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = scheme.primaryContainer,
                onClick = {
                    vm.setStage(OnboardingStage.TOUR)
                    onStartTour()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.guide_interactive_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = scheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(R.string.guide_interactive_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Box(
                        Modifier.size(44.dp).background(scheme.primary, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = stringResource(R.string.ob_start_tour), tint = scheme.onPrimary)
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
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                topics.forEach { topic ->
                    val stepCount = stringArrayResource(topic.stepsRes).size
                    ArnCard(Modifier.fillMaxWidth(), onClick = { onOpenTopic(topic.id) }, contentPadding = PaddingValues(Spacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).background(scheme.primaryContainer, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(topic.icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(Spacing.lg))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(topic.titleRes), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(topic.summaryRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(R.string.guide_topic_steps, stepCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.primary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/** Tutorial rinci satu fitur: langkah bernomor, tips, dan tombol untuk langsung mencobanya. */
@Composable
fun GuideTopicScreen(topicId: String, onBack: () -> Unit, onTry: (String) -> Unit) {
    val topic = GuideTopics.firstOrNull { it.id == topicId }
    val scheme = MaterialTheme.colorScheme
    if (topic == null) {
        onBack()
        return
    }
    val steps = stringArrayResource(topic.stepsRes)
    val tips = stringArrayResource(topic.tipsRes)
    Scaffold(
        containerColor = scheme.background,
        topBar = { ArnTopBar(stringResource(R.string.userguide_title), onBack) },
        bottomBar = {
            Surface(color = scheme.background) {
                PrimaryButton(
                    text = stringResource(R.string.guide_try),
                    onClick = { onTry(topic.openRoute) },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.lg),
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(topic.section.labelRes).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(topic.titleRes),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Box(
                    Modifier.size(56.dp).background(scheme.primaryContainer, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(topic.icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                }
            }
            Text(stringResource(topic.summaryRes), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)

            Text(
                stringResource(R.string.guide_steps_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
            )
            steps.forEachIndexed { i, text ->
                ArnCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(32.dp).background(scheme.primary, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
                        }
                        Spacer(Modifier.width(Spacing.lg))
                        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 5.dp))
                    }
                }
            }

            if (tips.isNotEmpty()) {
                Text(
                    stringResource(R.string.guide_tips_label),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
                )
                tips.forEach { tip ->
                    Surface(shape = RoundedCornerShape(18.dp), color = scheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.TipsAndUpdates, contentDescription = null, tint = scheme.onTertiaryContainer)
                            Spacer(Modifier.width(Spacing.md))
                            Text(tip, style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
