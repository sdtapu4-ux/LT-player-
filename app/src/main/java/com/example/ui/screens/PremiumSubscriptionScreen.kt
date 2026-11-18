package com.example.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import com.example.data.repository.PremiumUiState
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSubscriptionScreen(
    billingState: PremiumUiState,
    onLaunchPurchase: (Activity, ProductDetails) -> Unit,
    onRestorePurchases: () -> Unit,
    onRetryBilling: () -> Unit,
    onBack: () -> Unit
) {
    val activity = LocalContext.current as? Activity
    val perks = listOf(
        "100% Ad-free cinema and music experience",
        "Custom Studio themes & accent presets",
        "Advanced subtitle & playback speed presets",
        "Priority batch playlist & duplicate organizer"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LT PLAYER Ultimate") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF281D06), Color(0xFF0A1E36), MaterialTheme.colorScheme.surface)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = AmberGold, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = if (billingState.isPremiumActive) "Ultimate Membership Active" else "Upgrade to LT PLAYER Ultimate",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Managed securely by Google Play Billing. Cancel anytime in Play Store Subscriptions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Included Features", style = MaterialTheme.typography.titleMedium)
                        perks.forEach { perk ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(perk, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            if (billingState.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = ElectricGreen)
                    }
                }
            }

            if (billingState.products.isNotEmpty()) {
                items(billingState.products, key = { it.productId }) { product ->
                    val price = product.subscriptionOfferDetails
                        ?.firstOrNull()?.pricingPhases?.pricingPhaseList
                        ?.firstOrNull()?.formattedPrice ?: "Play Store Pricing"
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.title, style = MaterialTheme.typography.titleMedium)
                                Text(product.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(price, style = MaterialTheme.typography.titleSmall, color = ElectricGreen)
                            }
                            Button(
                                onClick = { activity?.let { onLaunchPurchase(it, product) } },
                                enabled = activity != null && !billingState.isPremiumActive,
                                colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black)
                            ) {
                                Text(if (billingState.isPremiumActive) "Active" else "Subscribe")
                            }
                        }
                    }
                }
            } else if (!billingState.isLoading) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Google Play Store Connection", style = MaterialTheme.typography.titleSmall, color = PremiumBlue)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = billingState.statusMessage
                                    ?: "Connect a Google Play Store account and publish subscription SKUs to purchase.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = onRetryBilling) {
                                Text("Refresh Play Store Products")
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onRestorePurchases,
                    modifier = Modifier.fillMaxWidth().testTag("restore_purchase_button")
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Restore Purchases")
                }
            }

            item {
                Text(
                    "Subscription Terms: Payment is charged to your Google Play account upon confirmation. Subscriptions renew automatically unless cancelled at least 24 hours before the end of the current period.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
