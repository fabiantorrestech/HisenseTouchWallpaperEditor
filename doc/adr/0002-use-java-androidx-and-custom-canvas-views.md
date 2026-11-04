# 2. Use Java, AndroidX and custom canvas views

Date: 2026-11-04

## Status

Accepted

## Context

The app runs on one sideloaded device, a Hisense Touch Lite (`HITV205N`) with an e-ink panel on Android 11. Its two editors need direct control over pinch, pan and zone-dragging, and the panel ghosts under frequent redraws. The owner's main stack is Java and Android. The options considered were Java with framework views only, Java with AndroidX, and Kotlin with Compose.

## Decision

We will write the app in Java with `androidx.appcompat` and `com.google.android.material`, using custom `View` subclasses that draw on a canvas for the crop and zone editors, with minSdk 30. The owner chose this over framework-only views and over Kotlin with Compose. That ready-made widgets were the reason for preferring AndroidX over framework-only views is inferred from the options as they were presented, not stated by the owner.

## Consequences

The code stays in the language the owner works in, and the editors control exactly when they redraw, which suits e-ink. Standard widgets come ready-made. Material's default ripples and transitions have to be turned off for the panel, and the two libraries add APK size that a single-device app gains little from. Moving to Compose later would mean rewriting the UI layer, though not the rendering or project storage code if those stay independent of the views.
