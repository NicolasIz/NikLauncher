package com.niklauncher.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.niklauncher.app.data.ProbeReport
import com.niklauncher.app.ui.LauncherViewModel
import com.niklauncher.app.ui.component.SectionHeader
import com.niklauncher.app.ui.component.StatusCard
import com.niklauncher.app.ui.component.StatusTone
import androidx.compose.material.icons.filled.Inventory2

/**
 * Runs the native probe and reports what it found.
 *
 * This is not a developer curiosity: until it passes on a real device, the
 * whole runtime-pack design is unproven, so the result is worth showing rather
 * than burying in a log.
 */
@Composable
fun DiagnosticsScreen(viewModel: LauncherViewModel) {
    val report by viewModel.probeReport.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader(
            title = "Diagnóstico",
            subtitle = "Comprueba en este dispositivo que se puede cargar una JVM descargada.",
        )

        Button(
            onClick = { viewModel.runNativeProbe(context) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (report == null) "Ejecutar comprobación" else "Repetir comprobación")
        }

        when (val current = report) {
            null -> Text(
                text = "Sin ejecutar todavía.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> ProbeResults(current)
        }

        SectionHeader(
            title = "Runtime de Java",
            subtitle = "De dónde salen la JVM y la capa gráfica.",
        )

        val runtime by viewModel.runtimeState.collectAsState()
        when {
            runtime.ready -> StatusCard(
                icon = Icons.Filled.CheckCircle,
                title = "Runtime instalado",
                body = runtime.installed.joinToString { it.displayName },
                tone = StatusTone.POSITIVE,
            )

            !runtime.hasPackSource -> StatusCard(
                icon = Icons.Filled.Inventory2,
                title = "Sin origen de runtime configurado",
                body = "El instalador de packs está listo y probado, pero todavía no hay un origen " +
                    "publicado del que descargarlos. Es una decisión de licencia pendiente, no un fallo.",
                tone = StatusTone.WARNING,
            )

            else -> StatusCard(
                icon = Icons.Filled.Inventory2,
                title = "Ningún runtime instalado",
                body = "Hay un origen configurado pero aún no se ha instalado ningún pack.",
                tone = StatusTone.NEUTRAL,
            )
        }
    }
}

@Composable
private fun ProbeResults(report: ProbeReport) {
    if (!report.libraryLoaded) {
        StatusCard(
            icon = Icons.Filled.ErrorOutline,
            title = "No se pudo cargar la librería nativa",
            body = report.failureReason ?: "Motivo desconocido",
            tone = StatusTone.WARNING,
        )
        return
    }

    StatusCard(
        icon = if (report.runtimeDesignViable) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
        title = if (report.runtimeDesignViable) {
            "El diseño de runtime es viable"
        } else {
            "Este dispositivo no permite el diseño previsto"
        },
        body = if (report.runtimeDesignViable) {
            "Se puede cargar código nativo desde el directorio de datos y obtener memoria ejecutable, " +
                "que es lo que necesita la JVM."
        } else {
            "Alguna de las condiciones necesarias para ejecutar una JVM descargada no se cumple. " +
                "Revisa el detalle de abajo."
        },
        tone = if (report.runtimeDesignViable) StatusTone.POSITIVE else StatusTone.WARNING,
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProbeRow(
            label = "dlopen desde el directorio de datos",
            value = if (report.dlopenFromDataDir) "correcto" else report.dlopenDetail,
            good = report.dlopenFromDataDir,
        )
        ProbeRow(
            label = "Memoria ejecutable (JIT)",
            value = report.executableMemory,
            good = report.executableMemory.contains("=yes"),
        )
        ProbeRow(
            label = "Tamaño de página del kernel",
            value = report.pageSizeLabel,
            good = report.pageSizeBytes > 0,
        )
        ProbeRow(
            label = "ABIs soportadas",
            value = report.supportedAbis.joinToString(", "),
            good = report.supportedAbis.any { it == "arm64-v8a" },
        )
    }

    if (report.pageSizeBytes == 16384) {
        Text(
            text = "Este dispositivo usa páginas de 16 KB: los runtime packs deberán estar alineados " +
                "a 16 KB o no se podrán mapear.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProbeRow(label: String, value: String, good: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = if (good) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}
