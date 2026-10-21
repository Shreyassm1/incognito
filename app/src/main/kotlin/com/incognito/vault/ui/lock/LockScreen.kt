package com.incognito.vault.ui.lock

import com.incognito.vault.ui.theme.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.incognito.vault.ui.theme.VaultAccent
import com.incognito.vault.ui.theme.VaultAccentDark
import com.incognito.vault.ui.theme.VaultBlack
import com.incognito.vault.ui.theme.VaultCard
import com.incognito.vault.ui.theme.VaultError
import com.incognito.vault.ui.theme.VaultSurface
import com.incognito.vault.ui.theme.VaultSurfaceVariant
import com.incognito.vault.ui.theme.VaultTextPrimary
import com.incognito.vault.ui.theme.VaultTextSecondary
import com.incognito.vault.viewmodel.VaultViewModel

@Composable
fun LockScreen(
    viewModel: VaultViewModel,
    onUnlocked: () -> Unit
) {
    val isSetup by viewModel.isSetup.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val lockoutSeconds by viewModel.lockoutSeconds.collectAsState()
    val biometricAvailable by viewModel.biometricAvailable.collectAsState()
    val biometricSaved by viewModel.biometricSaved.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isLocked) {
        if (!isLocked) {
            onUnlocked()
        }
    }

    // Auto-prompt biometrics if already setup & enrolled
    LaunchedEffect(isSetup, biometricSaved) {
        if (isSetup && biometricSaved && lockoutSeconds == 0) {
            val activity = context as? FragmentActivity
            activity?.let {
                viewModel.biometricHelper.promptBiometricUnlock(
                    activity = it,
                    onSuccess = { key ->
                        viewModel.unlockWithBiometricKey(key)
                    },
                    onError = { /* Ignored or user can type password */ }
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Shield Icon Header
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(VaultSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = VaultIcons.Shield,
                    contentDescription = "Vault Security",
                    tint = VaultAccent,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "INCOGNITO VAULT",
                color = VaultTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Text(
                text = if (isSetup) "Hardware-Isolated Local Storage" else "Initial Master Key Generation",
                color = VaultTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (!isSetup) {
                SetupVaultContent(
                    viewModel = viewModel,
                    biometricAvailable = biometricAvailable,
                    onSuccess = onUnlocked
                )
            } else {
                UnlockVaultContent(
                    viewModel = viewModel,
                    lockoutSeconds = lockoutSeconds,
                    biometricSaved = biometricSaved,
                    context = context,
                    keyboardController = keyboardController,
                    onSuccess = onUnlocked
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Security Invariant Badges
            SecurityBadges()
        }
    }
}

@Composable
private fun SetupVaultContent(
    viewModel: VaultViewModel,
    biometricAvailable: Boolean,
    onSuccess: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var enableBiometrics by remember { mutableStateOf(biometricAvailable) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val strength = calculatePasswordStrength(password)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VaultCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Create Master Password",
                color = VaultTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Used to derive AES-256 encryption keys via PBKDF2-HMAC-SHA256 (120,000 rounds). Never stored anywhere.",
                color = VaultTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorText = null
                },
                label = { Text("Master Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painter = if (passwordVisible) VaultIcons.VisibilityOff else VaultIcons.Visibility,
                            contentDescription = "Toggle password visibility",
                            tint = VaultTextSecondary
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultAccent,
                    unfocusedBorderColor = VaultSurface,
                    focusedTextColor = VaultTextPrimary,
                    unfocusedTextColor = VaultTextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Password strength bar
            if (password.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Strength: ${strength.label}",
                        color = strength.color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { strength.score },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = strength.color,
                    trackColor = VaultSurface,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    errorText = null
                },
                label = { Text("Confirm Master Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultAccent,
                    unfocusedBorderColor = VaultSurface,
                    focusedTextColor = VaultTextPrimary,
                    unfocusedTextColor = VaultTextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (biometricAvailable) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = VaultIcons.Fingerprint,
                            contentDescription = "Biometrics",
                            tint = VaultAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enable Fingerprint Unlock",
                            color = VaultTextPrimary,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = enableBiometrics,
                        onCheckedChange = { enableBiometrics = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VaultAccent,
                            checkedTrackColor = VaultSurface
                        )
                    )
                }
            }

            if (errorText != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorText ?: "",
                    color = VaultError,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (password.length < 6) {
                        errorText = "Password must be at least 6 characters"
                        return@Button
                    }
                    if (password != confirmPassword) {
                        errorText = "Passwords do not match"
                        return@Button
                    }

                    isProcessing = true
                    val success = viewModel.setupMasterPassword(password, enableBiometrics)
                    isProcessing = false
                    if (success) {
                        onSuccess()
                    }
                },
                enabled = !isProcessing && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultAccent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                } else {
                    Text(
                        text = "INITIALIZE SECURE VAULT",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun UnlockVaultContent(
    viewModel: VaultViewModel,
    lockoutSeconds: Int,
    biometricSaved: Boolean,
    context: android.content.Context,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    onSuccess: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    val isLockedOut = lockoutSeconds > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VaultCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Enter Master Password",
                color = VaultTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password / PIN") },
                singleLine = true,
                enabled = !isLockedOut && !isChecking,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        if (password.isNotEmpty() && !isLockedOut) {
                            isChecking = true
                            val ok = viewModel.unlockWithPassword(password)
                            isChecking = false
                            if (ok) {
                                password = ""
                                onSuccess()
                            }
                        }
                    }
                ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painter = if (passwordVisible) VaultIcons.VisibilityOff else VaultIcons.Visibility,
                            contentDescription = "Toggle password visibility",
                            tint = VaultTextSecondary
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultAccent,
                    unfocusedBorderColor = VaultSurface,
                    focusedTextColor = VaultTextPrimary,
                    unfocusedTextColor = VaultTextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(visible = isLockedOut) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Rate Limiting Active: Retry in ${lockoutSeconds}s",
                        color = VaultError,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    keyboardController?.hide()
                    isChecking = true
                    val ok = viewModel.unlockWithPassword(password)
                    isChecking = false
                    if (ok) {
                        password = ""
                        onSuccess()
                    }
                },
                enabled = !isLockedOut && !isChecking && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultAccent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                } else {
                    Text(
                        text = "UNLOCK VAULT",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            if (biometricSaved && !isLockedOut) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val activity = context as? FragmentActivity
                        activity?.let {
                            viewModel.biometricHelper.promptBiometricUnlock(
                                activity = it,
                                onSuccess = { key ->
                                    if (viewModel.unlockWithBiometricKey(key)) {
                                        onSuccess()
                                    }
                                },
                                onError = { /* handled */ }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultSurfaceVariant,
                        contentColor = VaultAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        painter = VaultIcons.Fingerprint,
                        contentDescription = "Biometric Unlock",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Unlock with Biometrics", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SecurityBadges() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BadgeItem(icon = VaultIcons.WifiOff, label = "Zero Internet")
            BadgeItem(icon = VaultIcons.Security, label = "FLAG_SECURE")
            BadgeItem(icon = VaultIcons.Lock, label = "AES-256-GCM")
        }
    }
}

@Composable
private fun BadgeItem(
    icon: androidx.compose.ui.graphics.painter.Painter,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(VaultSurface)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            painter = icon,
            contentDescription = label,
            tint = VaultAccent,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = VaultTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private data class PasswordStrength(
    val score: Float,
    val label: String,
    val color: Color
)

private fun calculatePasswordStrength(password: String): PasswordStrength {
    if (password.isEmpty()) return PasswordStrength(0f, "Empty", Color.Gray)
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++

    return when {
        score <= 2 -> PasswordStrength(0.33f, "Weak", VaultError)
        score in 3..4 -> PasswordStrength(0.66f, "Moderate", Color(0xFFFFB300))
        else -> PasswordStrength(1.0f, "Strong", VaultAccent)
    }
}
