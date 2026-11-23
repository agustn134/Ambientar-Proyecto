import 'package:flutter/material.dart';

/// Tokens oficiales del sistema visual GPago (`gpago_app/DESIGN.md`).
abstract final class GpagoTokens {
  static const Color canvas = Color(0xFFF4F7FB);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color primary = Color(0xFF17449E);
  static const Color primaryPressed = Color(0xFF103378);
  static const Color ink = Color(0xFF14233B);
  static const Color muted = Color(0xFF45566E);
  static const Color outline = Color(0xFF62738C);

  static const Color successInk = Color(0xFF166534);
  static const Color successBg = Color(0xFFDCFCE7);

  static const Color rejectedInk = Color(0xFF991B1B);
  static const Color rejectedBg = Color(0xFFFEE2E2);

  static const Color pendingInk = Color(0xFF854D0E);
  static const Color pendingBg = Color(0xFFFEF3C7);

  static const Color networkInk = Color(0xFF5B21B6);
  static const Color networkBg = Color(0xFFEDE9FE);

  static const double radiusControl = 12;
  static const double radiusCard = 16;
  static const double minTouchTarget = 48;
  static const double maxFormWidth = 480;
  static const double maxContentWidth = 1200;

  static const TextStyle titleStyle = TextStyle(
    fontFamily: 'Roboto',
    fontSize: 28,
    height: 36 / 28,
    fontWeight: FontWeight.w600,
    color: ink,
  );

  static const TextStyle subtitleStyle = TextStyle(
    fontFamily: 'Roboto',
    fontSize: 20,
    height: 28 / 20,
    fontWeight: FontWeight.w600,
    color: ink,
  );

  static const TextStyle bodyStyle = TextStyle(
    fontFamily: 'Roboto',
    fontSize: 16,
    height: 24 / 16,
    fontWeight: FontWeight.w400,
    color: ink,
  );

  static const TextStyle helpStyle = TextStyle(
    fontFamily: 'Roboto',
    fontSize: 14,
    height: 20 / 14,
    fontWeight: FontWeight.w400,
    color: muted,
  );

  static const TextStyle amountStyle = TextStyle(
    fontFamily: 'Roboto',
    fontSize: 32,
    height: 40 / 32,
    fontWeight: FontWeight.w700,
    color: ink,
    fontFeatures: [FontFeature.tabularFigures()],
  );

  static const TextStyle folioStyle = TextStyle(
    fontFamily: 'monospace',
    fontSize: 14,
    height: 20 / 14,
    fontWeight: FontWeight.w400,
    color: ink,
  );

  static String formatMxn(double monto) {
    final negative = monto < 0;
    final fixed = monto.abs().toStringAsFixed(2);
    final parts = fixed.split('.');
    final intPart = parts[0];
    final buf = StringBuffer();
    for (var i = 0; i < intPart.length; i++) {
      final remaining = intPart.length - i;
      if (i > 0 && remaining % 3 == 0) {
        buf.write(',');
      }
      buf.write(intPart[i]);
    }
    final sign = negative ? '-' : '';
    return '$sign\$${buf.toString()}.${parts[1]} MXN';
  }

  static String formatServerTimestamp(DateTime ts) {
    final local = ts.toLocal();
    final y = local.year.toString().padLeft(4, '0');
    final m = local.month.toString().padLeft(2, '0');
    final d = local.day.toString().padLeft(2, '0');
    final hh = local.hour.toString().padLeft(2, '0');
    final mm = local.minute.toString().padLeft(2, '0');
    final ss = local.second.toString().padLeft(2, '0');
    return '$d/$m/$y $hh:$mm:$ss';
  }

  static ThemeData lightTheme() {
    const scheme = ColorScheme(
      brightness: Brightness.light,
      primary: primary,
      onPrimary: Colors.white,
      secondary: primaryPressed,
      onSecondary: Colors.white,
      error: rejectedInk,
      onError: Colors.white,
      surface: surface,
      onSurface: ink,
      outline: outline,
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: canvas,
      materialTapTargetSize: MaterialTapTargetSize.padded,
      fontFamily: 'Roboto',
      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(
          minimumSize: const Size(minTouchTarget, minTouchTarget),
        ),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: surface,
        foregroundColor: ink,
        elevation: 0,
        surfaceTintColor: Colors.transparent,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        color: surface,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(radiusCard),
          side: const BorderSide(color: Color(0xFFD7E0EC)),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surface,
        labelStyle: const TextStyle(color: ink, fontWeight: FontWeight.w500),
        helperStyle: helpStyle,
        errorStyle: const TextStyle(color: rejectedInk, fontSize: 13),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 14,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusControl),
          borderSide: const BorderSide(color: outline),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusControl),
          borderSide: const BorderSide(color: outline),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusControl),
          borderSide: const BorderSide(color: primary, width: 2),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusControl),
          borderSide: const BorderSide(color: rejectedInk, width: 1.5),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusControl),
          borderSide: const BorderSide(color: rejectedInk, width: 2),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ButtonStyle(
          minimumSize: const WidgetStatePropertyAll(
            Size(minTouchTarget, minTouchTarget),
          ),
          backgroundColor: WidgetStateProperty.resolveWith((states) {
            if (states.contains(WidgetState.disabled)) {
              return outline.withValues(alpha: 0.4);
            }
            if (states.contains(WidgetState.pressed)) {
              return primaryPressed;
            }
            return primary;
          }),
          foregroundColor: const WidgetStatePropertyAll(Colors.white),
          shape: WidgetStatePropertyAll(
            RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(radiusControl),
            ),
          ),
          textStyle: const WidgetStatePropertyAll(
            TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(minTouchTarget, minTouchTarget),
          foregroundColor: ink,
          side: const BorderSide(color: outline),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(radiusControl),
          ),
          textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          minimumSize: const Size(minTouchTarget, minTouchTarget),
          foregroundColor: primary,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(radiusControl),
          ),
          textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
        ),
      ),
    );
  }
}

enum GpagoBannerTone { success, rejected, pending, network }

class GpagoStatusBanner extends StatelessWidget {
  const GpagoStatusBanner({
    super.key,
    required this.tone,
    required this.title,
    required this.description,
    this.action,
  });

  final GpagoBannerTone tone;
  final String title;
  final String description;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final (Color inkColor, Color bgColor, IconData icon) = switch (tone) {
      GpagoBannerTone.success => (
        GpagoTokens.successInk,
        GpagoTokens.successBg,
        Icons.check_circle_outline,
      ),
      GpagoBannerTone.rejected => (
        GpagoTokens.rejectedInk,
        GpagoTokens.rejectedBg,
        Icons.block,
      ),
      GpagoBannerTone.pending => (
        GpagoTokens.pendingInk,
        GpagoTokens.pendingBg,
        Icons.schedule,
      ),
      GpagoBannerTone.network => (
        GpagoTokens.networkInk,
        GpagoTokens.networkBg,
        Icons.wifi_off_outlined,
      ),
    };

    return Semantics(
      liveRegion: true,
      container: true,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: bgColor,
          borderRadius: BorderRadius.circular(GpagoTokens.radiusCard),
          border: Border.all(color: inkColor.withValues(alpha: 0.35)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(icon, color: inkColor, size: 22),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    title,
                    style: GpagoTokens.bodyStyle.copyWith(
                      color: inkColor,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              description,
              style: GpagoTokens.helpStyle.copyWith(
                color: GpagoTokens.ink,
              ),
            ),
            if (action != null) ...[
              const SizedBox(height: 12),
              action!,
            ],
          ],
        ),
      ),
    );
  }
}
