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

  /// 사용량 통계 권한 획득
  static Future<bool> getUsageStatsPermission() async {
    return await _channel.invokeMethod('getUsageStatsPermission');
  }

  /// 배터리 최적화 무시 요청
  static Future<bool> requestIgnoreBatteryOptimizations() async {
    return await _channel.invokeMethod('requestIgnoreBatteryOptimizations');
  }

  /// 배터리 최적화 무시 여부 확인
  static Future<bool> isIgnoringBatteryOptimizations() async {
    return await _channel.invokeMethod('isIgnoringBatteryOptimizations');
  }

  /// 다른 앱 위에 표시 설정 화면으로 이동
  static Future<void> openOverlaySettings() async {
    try {
      await _channel.invokeMethod('openOverlaySettings');
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (openOverlaySettings): $e');
    }
  }

  /// 백그라운드 서비스 활성화 여부 확인 (접근성, 알림, 오버레이 3가지)
  static Future<Map<String, bool>> checkServicesEnabled() async {
    try {
      final Map<Object?, Object?>? result = await _channel.invokeMethod('checkServicesEnabled');
      if (result != null) {
        return {
          'accessibility': result['accessibility'] == true,
          'notification': result['notification'] == true,
          'overlay': result['overlay'] == true,
        };
      }
      return {'accessibility': false, 'notification': false, 'overlay': false};
    } on PlatformException {
      debugPrint('NativeBridge Error (checkServicesEnabled)');
      return {'accessibility': false, 'notification': false, 'overlay': false};
    }
  }

  /// (테스트용) 저장된 로컬 DB 메시지 수 조회
  static Future<int> getMessageCount() async {
    try {
      final int count = await _channel.invokeMethod('getMessageCount');
      return count;
    } on PlatformException {
      debugPrint('NativeBridge Error (getMessageCount)');
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

  /// 채팅방 목록 조회 (roomId 기준 그루핑)
  static Future<List<Map<String, dynamic>>> getChatRooms() async {
    try {
      final List<dynamic> result = await _channel.invokeMethod('getChatRooms');
      return result.map((e) => Map<String, dynamic>.from(e)).toList();
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (getChatRooms): $e');
      return [];
    }
  }

  /// 특정 채팅방 메시지 페이지네이션 조회 (최신 순)
  static Future<List<Map<String, dynamic>>> getChatMessages(String roomId, {int limit = 50, int offset = 0}) async {
    try {
      final List<dynamic> result = await _channel.invokeMethod(
        'getChatMessages',
        {'roomId': roomId, 'limit': limit, 'offset': offset},
      );
      return result.map((e) => Map<String, dynamic>.from(e)).toList();
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (getChatMessages): $e');
      return [];
    }
  }

  /// AI 답장 생성 (3가지 페르소나)
  static Future<List<String>> generateAiReply(String roomId) async {
    try {
      final List<dynamic> result = await _channel.invokeMethod(
        'generateAiReply',
        {'roomId': roomId},
      );
      return result.cast<String>();
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (generateAiReply): $e');
      return ['네, 확인했습니다.', '지금은 어렵습니다.', '글쎄요, 조금 더 생각해볼게요.'];
    }
  }

  /// 직접 답장 가능 여부 확인 (RemoteInput 존재 여부)
  static Future<bool> canDirectReply(String roomId) async {
    try {
      final bool result = await _channel.invokeMethod(
        'canDirectReply',
        {'roomId': roomId},
      );
      return result;
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (canDirectReply): $e');
      return false;
    }
  }

  /// RemoteInput을 통해 직접 답장 전송
  static Future<bool> sendDirectReply(String roomId, String text) async {
    try {
      final bool result = await _channel.invokeMethod(
        'sendDirectReply',
        {'roomId': roomId, 'text': text},
      );
      return result;
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (sendDirectReply): $e');
      return false;
    }
  }

  /// 채팅방 규칙 저장
  static Future<bool> saveRoomRule(String roomId, String rule) async {
    try {
      final bool result = await _channel.invokeMethod(
        'saveRoomRule',
        {'roomId': roomId, 'rule': rule},
      );
      return result;
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (saveRoomRule): $e');
      return false;
    }
  }

  /// 채팅방 규칙 조회
  static Future<String?> getRoomRule(String roomId) async {
    try {
      final String? result = await _channel.invokeMethod(
        'getRoomRule',
        {'roomId': roomId},
      );
      return result;
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (getRoomRule): $e');
      return null;
    }
  }

  /// 채팅방 삭제 (메시지 및 규칙 모두 삭제)
  static Future<bool> deleteChatRoom(String roomId) async {
    try {
      final bool result = await _channel.invokeMethod(
        'deleteChatRoom',
        {'roomId': roomId},
      );
      return result;
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (deleteChatRoom): $e');
      return false;
    }
  }

  /// 모든 채팅 메시지 및 규칙 삭제 (전체 초기화)
  static Future<bool> deleteAllMessages() async {
    try {
      final bool result = await _channel.invokeMethod('deleteAllMessages');
      return result;
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (deleteAllMessages): $e');
      return false;
    }
  }

  /// 기존 'kakao_' 접두사 데이터 마이그레이션
  static Future<bool> migrateOldRoomIds() async {
    try {
      final bool result = await _channel.invokeMethod('migrateOldRoomIds');
      return result;
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (migrateOldRoomIds): $e');
      return false;
    }
  }

  /// Gemini API Key 설정 (에뮬레이터 테스트용 클라우드 Fallback 활성화)
  static Future<bool> setGeminiApiKey(String apiKey) async {
    try {
      final bool result = await _channel.invokeMethod(
        'setGeminiApiKey',
        {'apiKey': apiKey},
      );
      return result;
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (setGeminiApiKey): $e');
      return false;
    }
  }

  /// 카카오톡 텍스트 파일 파싱 및 DB 저장
  static Future<bool> processFile(String filePath, {String? roomName, String meSenderName = '나'}) async {
    try {
      final bool result = await _channel.invokeMethod(
        'processFile',
        {
          'filePath': filePath,
          'roomName': roomName,
          'meSenderName': meSenderName,
        },
      );
      return result;
    } on PlatformException catch (e) {
      debugPrint('NativeBridge Error (processFile): $e');
      return false;
    }
  }
}
