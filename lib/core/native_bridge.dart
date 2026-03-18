import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

class NativeBridge {
  static const MethodChannel _channel = MethodChannel('com.jonghyun.autome/native');

  /// 접근성 서비스 설정 화면으로 이동
  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibilitySettings');
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (openAccessibilitySettings): $e');
    }
  }

  /// 알림 접근 허용 설정 화면으로 이동
  static Future<void> openNotificationSettings() async {
    try {
      await _channel.invokeMethod('openNotificationSettings');
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (openNotificationSettings): $e');
    }
  }

  /// 백그라운드 서비스 활성화 여부 확인
  static Future<bool> checkServicesEnabled() async {
    try {
      final bool result = await _channel.invokeMethod('checkServicesEnabled');
      return result;
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (checkServicesEnabled): $e');
      return false;
    }
  }

  /// (테스트용) 저장된 로컬 DB 메시지 수 조회
  static Future<int> getMessageCount() async {
    try {
      final int count = await _channel.invokeMethod('getMessageCount');
      return count;
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (getMessageCount): $e');
      return 0;
    }
  }

  /// (테스트용) 최신 수집된 메시지 목록 조회
  static Future<List<Map<String, dynamic>>> getLatestMessages() async {
    try {
      final List<dynamic> result = await _channel.invokeMethod('getLatestMessages');
      return result.map((e) => Map<String, dynamic>.from(e)).toList();
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (getLatestMessages): $e');
      return [];
    }
  }
}
