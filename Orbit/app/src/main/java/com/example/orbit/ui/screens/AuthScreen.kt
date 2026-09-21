package com.example.orbit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.orbit.R
import com.example.orbit.ui.stateholders.AuthMode
import com.example.orbit.ui.stateholders.AuthViewModel
import com.example.orbit.ui.theme.orbitAccents
import com.example.orbit.ui.theme.warmShadow

private val LOGO_SIZE = 96.dp
private val INNER_RING_SIZE = 136.dp
private val OUTER_RING_SIZE = 184.dp

/** Prstenovi samo nagovestavaju orbitu; logotip mora da ostane glavni */
private const val RING_ALPHA = 0.3f

/** Koliko brend boja ulazi u pozadinu; vise od ovoga obara kontrast zelenog naslova ispod 3:1 */
private const val TOP_TINT = 0.20f
private const val MIDDLE_TINT = 0.12f

/** F-13: prijava, registracija i nova lozinka na jednom ekranu */
@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signingUp = state.mode == AuthMode.SIGN_UP
    val resetting = state.mode == AuthMode.RESET
    val accent = MaterialTheme.orbitAccents.brandStart

    // Registracija i zamena lozinke traze potvrdu nove lozinke
    val confirmsPassword = signingUp || resetting

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(authBackground())
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            LogoWithOrbits()

            Text(
                text = stringResource(R.string.app_name),
                // Jedini ekran gde ime aplikacije sme da bude ovoliko
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(
                    when (state.mode) {
                        AuthMode.LOG_IN -> R.string.auth_login_title
                        AuthMode.SIGN_UP -> R.string.auth_signup_title
                        AuthMode.RESET -> R.string.auth_reset_title
                    }
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            if (resetting) {
                Text(
                    text = stringResource(R.string.auth_reset_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(8.dp))

            if (signingUp) {
                AuthField(
                    value = state.displayName,
                    onValueChange = viewModel::onDisplayNameChange,
                    label = stringResource(R.string.account_display_name),
                    icon = Icons.Filled.Person,
                    error = state.displayNameError,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
            }

            AuthField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(R.string.auth_field_email),
                icon = Icons.Filled.Email,
                error = state.emailError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )

            AuthField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(
                    if (resetting) R.string.auth_field_new_password else R.string.auth_field_password
                ),
                icon = Icons.Filled.Lock,
                error = state.passwordError,
                isPassword = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (confirmsPassword) ImeAction.Next else ImeAction.Done,
                ),
                onDone = viewModel::submit,
            )

            // Odmah ispod lozinke, jer se tu korisnik seti da je ne zna
            if (state.mode == AuthMode.LOG_IN) {
                TextButton(
                    onClick = viewModel::startPasswordReset,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.auth_forgot_password))
                }
            }

            if (confirmsPassword) {
                AuthField(
                    value = state.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = stringResource(R.string.auth_field_confirm_password),
                    icon = Icons.Filled.Lock,
                    error = state.confirmPasswordError,
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    onDone = viewModel::submit,
                )
            }

            state.error?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = MaterialTheme.orbitAccents.onCategory,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .warmShadow(
                        elevation = 4.dp,
                        shape = CircleShape,
                        color = lerp(MaterialTheme.orbitAccents.shadow, accent, 0.5f),
                    ),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.orbitAccents.onCategory,
                    )
                } else {
                    Text(
                        text = stringResource(
                            when (state.mode) {
                                AuthMode.LOG_IN -> R.string.auth_login_action
                                AuthMode.SIGN_UP -> R.string.auth_signup_action
                                AuthMode.RESET -> R.string.auth_reset_action
                            }
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = if (resetting) viewModel::showLogIn else viewModel::toggleMode,
                enabled = !state.isSubmitting,
            ) {
                Text(
                    stringResource(
                        when (state.mode) {
                            AuthMode.LOG_IN -> R.string.auth_switch_to_signup
                            AuthMode.SIGN_UP -> R.string.auth_switch_to_login
                            AuthMode.RESET -> R.string.auth_back_to_login
                        }
                    )
                )
            }
        }
    }
}

/**
 * Jedno polje forme: belo, sa ikonicom i tankim okvirom koji pozeleni kad je polje aktivno.
 * Isti izgled na sva tri ekrana, jer sva polja idu kroz ovu funkciju.
 */
@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    /** Id poruke o gresci iz ViewModel-a; null kad je polje ispravno */
    error: Int?,
    keyboardOptions: KeyboardOptions,
    isPassword: Boolean = false,
    onDone: () -> Unit = {},
) {
    val accent = MaterialTheme.orbitAccents.brandStart
    val surface = MaterialTheme.colorScheme.surface

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        singleLine = true,
        isError = error != null,
        // Prazan red ostaje i bez greske, pa forma ne skace kad se greska pojavi
        supportingText = { error?.let { Text(stringResource(it)) } },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = surface,
            unfocusedContainerColor = surface,
            errorContainerColor = surface,
            focusedBorderColor = accent,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = accent,
            focusedLeadingIconColor = accent,
            cursorColor = accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Plavo gore kao okean na logotipu, zeleno u sredini kao kopno, a forma na mirnoj pozadini */
@Composable
private fun authBackground(): Brush {
    val accents = MaterialTheme.orbitAccents
    val background = MaterialTheme.colorScheme.background
    return Brush.verticalGradient(
        listOf(
            lerp(background, accents.brandEnd, TOP_TINT),
            lerp(background, accents.brandStart, MIDDLE_TINT),
            background,
        )
    )
}

/** Logotip u sredini dve orbite, sa tri "planete" u bojama pinova sa ikonice */
@Composable
private fun LogoWithOrbits() {
    val ringColor = MaterialTheme.orbitAccents.brandStart.copy(alpha = RING_ALPHA)

    Box(
        modifier = Modifier.size(OUTER_RING_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(OUTER_RING_SIZE)
                .border(width = 1.5.dp, color = ringColor, shape = CircleShape)
        )
        Box(
            Modifier
                .size(INNER_RING_SIZE)
                .border(width = 1.5.dp, color = ringColor, shape = CircleShape)
        )

        // Pomeraji od centra su izracunati da tacka padne tacno na prsten
        Planet(x = 65.dp, y = (-65).dp, size = 14.dp, color = MaterialTheme.orbitAccents.brandEnd)
        Planet(x = (-46).dp, y = 80.dp, size = 10.dp, color = MaterialTheme.colorScheme.tertiary)
        Planet(x = (-68).dp, y = 0.dp, size = 8.dp, color = MaterialTheme.colorScheme.error)

        // Ikonica je adaptivna (XML), pa je painterResource ne ume; Coil je sklapa
        AsyncImage(
            model = R.mipmap.ic_launcher_round,
            contentDescription = null,
            modifier = Modifier
                .size(LOGO_SIZE)
                .warmShadow(elevation = 8.dp, shape = CircleShape)
                .border(width = 4.dp, color = MaterialTheme.colorScheme.surface, shape = CircleShape)
                .clip(CircleShape),
        )
    }
}

@Composable
private fun Planet(x: Dp, y: Dp, size: Dp, color: Color) {
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(size)
            .background(color = color, shape = CircleShape)
    )
}
