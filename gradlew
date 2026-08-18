#!/bin/sh
# Lightweight wrapper matching MobFarmBlock. CI installs Gradle with setup-gradle.
exec gradle "$@"
