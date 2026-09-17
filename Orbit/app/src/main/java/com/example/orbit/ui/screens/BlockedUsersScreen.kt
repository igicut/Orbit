package com.example.orbit.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.R
import com.example.orbit.ui.components.EmptyView
import com.example.orbit.ui.stateholders.AccountViewModel

/** F-28: blokirani korisnici, odvojeno od dogadjaja i sa svojim skrolom */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Odblokiranje trazi mrezu; neuspeh se javlja ovde, gde je i dugme
    val actionErrorMessage = actionError?.let { stringResource(it) }
    LaunchedEffect(actionErrorMessage) {
        actionErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearActionError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_blocked_users)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { innerPadding ->
        if (blockedUsers.isEmpty()) {
            EmptyView(
                title = stringResource(R.string.account_blocked_users),
                subtitle = stringResource(R.string.account_blocked_empty),
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = blockedUsers, key = { it.blockedId }) { blocked ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                // Ime ako ga imamo, inace skraceni id
                                text = blocked.displayName ?: blocked.blockedId.take(8),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            TextButton(onClick = { viewModel.unblock(blocked.blockedId) }) {
                                Text(stringResource(R.string.account_unblock))
                            }
                        }
                    }
                }
            }
        }
    }
}
