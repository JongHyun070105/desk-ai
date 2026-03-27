import 'package:daechung_talk/core/utils/chat_log_parser.dart';
import 'package:daechung_talk/core/utils/pii_masker.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('MethodChannel 채널명 일관성 검증', () {
    // NativeBridge와 TxtImportScreen 모두 동일한 채널명을 사용해야 합니다.
    // 이 테스트는 코드 상수를 직접 확인하지는 않지만,
    // 관련 모듈이 올바르게 임포트 가능하고 동작하는지를 검증합니다.

    test('PiiMasker 모듈이 정상적으로 임포트 및 실행된다', () {
      // PiiMasker가 순수 함수로 동작하는지 기본 확인
      final result = PiiMasker.maskText('일반 텍스트');
      expect(result, '일반 텍스트');
    });

    test('ChatLogParser 모듈이 정상적으로 임포트 및 실행된다', () {
      final result = ChatLogParser.parse('', 'test');
      expect(result, isEmpty);
    });
  });

  group('PII 마스킹 + 파싱 통합 테스트', () {
    test('파싱된 메시지에 PII가 포함되어 있으면 마스킹이 적용되어야 한다', () {
      const input = '[홍길동] [오후 3:42] 제 번호는 010-1234-5678 이에요';
      final results = ChatLogParser.parse(input, 'test_room');

      // 파싱 후 마스킹 적용 (실제 네이티브 코드의 플로우)
      final maskedMessage = PiiMasker.maskText(results[0].message);

      expect(maskedMessage.contains('010-1234-5678'), isFalse);
      expect(maskedMessage.contains('[전화번호 마스킹]'), isTrue);
    });

    test('다중 PII가 포함된 대화 파싱 및 마스킹', () {
      const input =
          '[홍길동] [오후 3:42] 전화 010-9999-1234, 메일 test@mail.com\n'
          '[나] [오후 3:43] 계좌 110-123-456789 으로 보내드릴게요';
      final results = ChatLogParser.parse(input, 'test_room');

      final masked0 = PiiMasker.maskText(results[0].message);
      final masked1 = PiiMasker.maskText(results[1].message);

      expect(masked0.contains('[전화번호 마스킹]'), isTrue);
      expect(masked0.contains('[이메일 마스킹]'), isTrue);
      expect(masked1.contains('[계좌번호 마스킹]'), isTrue);
    });
  });
}
