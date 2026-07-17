package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DesktopActivationScreen(
    onActivated: () -> Unit,
) {
    var email by remember { mutableStateOf(DesktopLicenseStore.savedEmail()) }
    var licenseKey by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Enter your desktop license to unlock DHC-6 Trainer Desktop ${DesktopBuildInfo.VersionName}.") }
    var isError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        Dhc6DesktopColors.BackgroundDeep,
                        Dhc6DesktopColors.Background,
                        Dhc6DesktopColors.BackgroundDeep,
                    )
                )
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.width(620.dp),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, Dhc6DesktopColors.BorderBright),
            colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Surface),
        ) {
            Column(
                modifier = Modifier.padding(30.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(58.dp)
                            .height(58.dp)
                            .background(Dhc6DesktopColors.AccentStrong, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("6", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
                    }

                    Spacer(Modifier.width(16.dp))

                    Column {
                        Text(
                            "DHC-6 Trainer Desktop",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            "License activation required - ${DesktopBuildInfo.VersionName}",
                            color = Dhc6DesktopColors.TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                HorizontalDivider(color = Dhc6DesktopColors.Border)

                Text(
                    "This desktop edition is available only to licensed users, instructors, and approved training organizations.",
                    color = Dhc6DesktopColors.TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    placeholder = { Text("name@example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = licenseKey,
                    onValueChange = { licenseKey = it },
                    label = { Text("Desktop license key") },
                    placeholder = { Text("DHC6-DESKTOP-169-TJAERO-CHECKSUM") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    message,
                    color = if (isError) Dhc6DesktopColors.Red else Dhc6DesktopColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            val result = DesktopLicenseStore.activate(email, licenseKey)
                            message = result.message
                            isError = !result.success
                            if (result.success) {
                                onActivated()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.AccentStrong),
                    ) {
                        Text("Activate Desktop", color = Color.White, fontWeight = FontWeight.Black)
                    }

                    OutlinedButton(
                        onClick = {
                            email = ""
                            licenseKey = ""
                            message = "Enter your desktop license to unlock DHC-6 Trainer Desktop ${DesktopBuildInfo.VersionName}."
                            isError = false
                        },
                    ) {
                        Text("Clear", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
                    colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("TRAINING USE ONLY", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text(
                            "DHC-6 Trainer is a study and training-support tool. Always use approved aircraft manuals, company procedures, and applicable regulations for real operations.",
                            color = Dhc6DesktopColors.TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
        }
    }
}
