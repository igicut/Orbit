package com.example.orbit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.orbit.R
import com.example.orbit.ui.stateholders.AuthMode
import com.example.orbit.ui.stateholders.AuthViewModel

private val LOGO_SIZE = 88.dp

/** Pozadinski pinovi samo nagovestavaju motiv sa ikonice; forma mora da ostane glavna */
private const val MOTIF_ALPHA = 0.18f

/** F-13: prijava i registracija na jednom ekranu */
@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signingUp = state.mode == AuthMode.SIGN_UP

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {

            PinBackdrop()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {

                // Ikonica je adaptivna (XML), pa je painterResource ne ume; Coil je sklapa
                AsyncImage(
                    model = R.mipmap.ic_launcher_round,
                    contentDescription = null,
                    modifier = Modifier
                        .size(LOGO_SIZE)
                        .clip(CircleShape),
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    // Jedini ekran gde ime aplikacije sme da bude ovoliko
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.auth_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(
                        if (signingUp) R.string.auth_signup_title else R.string.auth_login_title
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )

                if (signingUp) {
                    TextField(
                        value = state.displayName,
                        onValueChange = viewModel::onDisplayNameChange,
                        label = { Text(stringResource(R.string.account_display_name)) },
                        singleLine = true,
                        isError = state.displayNameError != null,
                        supportingText = { state.displayNameError?.let { Text(stringResource(it)) } },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        shape = MaterialTheme.shapes.small,
                        colors = authFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                TextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text(stringResource(R.string.auth_field_email)) },
                    singleLine = true,
                    isError = state.emailError != null,
                    supportingText = { state.emailError?.let { Text(stringResource(it)) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    shape = MaterialTheme.shapes.small,
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                TextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(stringResource(R.string.auth_field_password)) },
                    singleLine = true,
                    isError = state.passwordError != null,
                    supportingText = { state.passwordError?.let { Text(stringResource(it)) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (signingUp) ImeAction.Next else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                    shape = MaterialTheme.shapes.small,
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (signingUp) {
                    TextField(
                        value = state.confirmPassword,
                        onValueChange = viewModel::onConfirmPasswordChange,
                        label = { Text(stringResource(R.string.auth_field_confirm_password)) },
                        singleLine = true,
                        isError = state.confirmPasswordError != null,
                        supportingText = {
                            state.confirmPasswordError?.let { Text(stringResource(it)) }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                        shape = MaterialTheme.shapes.small,
                        colors = authFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                state.error?.let {
                    Text(
                        text = stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Button(
                    onClick = viewModel::submit,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            stringResource(
                                if (signingUp) R.string.auth_signup_action else R.string.auth_login_action
                            )
                        )
                    }
                }

                TextButton(
                    onClick = viewModel::toggleMode,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        stringResource(
                            if (signingUp) R.string.auth_switch_to_login else R.string.auth_switch_to_signup
                        )
                    )
                }
            }
        }
    }
}

/** Polja su ispunjena povrsinskom bojom; na kremu providno polje ne izgleda kao polje */
@Composable
private fun authFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    errorContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
)

/**
 * Razbacani pinovi u gornjem delu, motiv sa ikonice aplikacije.
 * Boje se uzimaju iz seme (`error`/`tertiary`/`secondary` su bas tri akcenta),
 * pa tamna tema dobija svoje parnjake bez posebnih vrednosti.
 */
@Composable
private fun PinBackdrop() {
    val terracotta = MaterialTheme.colorScheme.error
    val mustard = MaterialTheme.colorScheme.tertiary
    val dustyBlue = MaterialTheme.colorScheme.secondary

    Box(modifier = Modifier.fillMaxSize()) {
        // Razmesteni desno od logotipa i uz desnu ivicu, da ne padnu iza naslova i podnaslova
        MotifPin(Alignment.TopStart, 112.dp, 28.dp, 18.dp, 18f, dustyBlue)
        MotifPin(Alignment.TopStart, 196.dp, 96.dp, 24.dp, -14f, mustard)
        MotifPin(Alignment.TopEnd, (-40).dp, 40.dp, 20.dp, 10f, terracotta)
        MotifPin(Alignment.TopEnd, (-108).dp, 128.dp, 15.dp, -6f, dustyBlue)
        MotifPin(Alignment.TopEnd, (-32).dp, 176.dp, 22.dp, 8f, mustard)
        MotifPin(Alignment.TopEnd, (-132).dp, 218.dp, 14.dp, -20f, terracotta)
    }
}

@Composable
private fun BoxScope.MotifPin(
    alignment: Alignment,
    x: Dp,
    y: Dp,
    size: Dp,
    rotation: Float,
    color: Color,
) {
    Icon(
        painter = painterResource(R.drawable.ic_map_pin),
        contentDescription = null,
        tint = color.copy(alpha = MOTIF_ALPHA),
        modifier = Modifier
            .align(alignment)
            .offset(x = x, y = y)
            .rotate(rotation)
            .size(size),
    )
}
