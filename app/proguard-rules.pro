# ViewBinding-классы вызываются рефлексией из сгенерированного кода
-keep class cloud.xvps.converter.databinding.** { *; }

# org.json входит в состав Android, предупреждения не нужны
-dontwarn org.json.**
