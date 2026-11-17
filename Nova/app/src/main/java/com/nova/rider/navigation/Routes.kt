package com.nova.rider.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every rider screen is a type-safe, serializable Navigation 3 key, so the back stack survives
 * configuration changes and process death (rememberNavBackStack).
 */
@Serializable sealed interface Route : NavKey

// Launch, onboarding & auth
@Serializable data object Splash : Route
@Serializable data object Onboarding1 : Route
@Serializable data object Onboarding2 : Route
@Serializable data object Onboarding3 : Route
@Serializable data object Welcome : Route
@Serializable data object SignUpPhone : Route
@Serializable data object Login : Route
/** Code entry for one channel. [purpose] is "signup" or "login"; [destination] is the E.164 phone or the email. */
@Serializable data class Otp(val channel: String, val destination: String, val purpose: String) : Route
@Serializable data object LocationPermission : Route
@Serializable data object ProfileSetup : Route

// Booking & trip
@Serializable data object Home : Route
@Serializable data object Search : Route
@Serializable data object PinMap : Route
@Serializable data object AddStop : Route
@Serializable data object RideOptions : Route
@Serializable data object Schedule : Route
@Serializable data object FindingDriver : Route
@Serializable data object DriverEnRoute : Route
@Serializable data object Chat : Route
@Serializable data object Call : Route
@Serializable data object ShareTrip : Route
@Serializable data object CancelRide : Route
@Serializable data object OnTrip : Route
@Serializable data object TripComplete : Route

// Activity, wallet, account & support
@Serializable data object Activity : Route
@Serializable data object Receipt : Route
@Serializable data object Wallet : Route
@Serializable data object AddCard : Route
@Serializable data object Account : Route
@Serializable data object EditProfile : Route
@Serializable data object SavedPlaces : Route
@Serializable data object Notifications : Route
@Serializable data object Offers : Route
@Serializable data object Settings : Route
@Serializable data object Safety : Route
@Serializable data object Help : Route
@Serializable data object SupportChat : Route
@Serializable data object LostItem : Route

// Edge cases
@Serializable data object NoDrivers : Route
@Serializable data object Surge : Route
@Serializable data object DriverCancelled : Route
@Serializable data object PaymentFailed : Route
@Serializable data object Offline : Route
@Serializable data object LocationDenied : Route
@Serializable data object NoService : Route
@Serializable data object EmptyActivity : Route
@Serializable data object SosActive : Route
