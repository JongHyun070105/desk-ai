import 'package:flutter/material.dart';
import 'package:daechung_talk/core/theme/app_theme.dart';

/// 모던 소프트 카드 위젯
/// 소프트 섀도우 + 미세 보더로 Apple 스타일 카드 구현
class SoftCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry? padding;
  final EdgeInsetsGeometry? margin;
  final double borderRadius;
  final VoidCallback? onTap;
  final Color? color;

  const SoftCard({
    super.key,
    required this.child,
    this.padding,
    this.margin,
    this.borderRadius = 20,
    this.onTap,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: margin ?? EdgeInsets.zero,
      child: GestureDetector(
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: padding ?? const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: color ?? context.cardColor,
            borderRadius: BorderRadius.circular(borderRadius),
            border: Border.all(color: context.cardBorder, width: 1),
            boxShadow: context.isDark
                ? []
                : [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.04),
                      blurRadius: 20,
                      offset: const Offset(0, 4),
                    ),
                  ],
          ),
          child: child,
        ),
      ),
    );
  }
}
