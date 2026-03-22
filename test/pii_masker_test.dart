import 'package:flutter_test/flutter_test.dart';
import 'package:auto_me/core/utils/pii_masker.dart';

void main() {
  group('PiiMasker 테스트', () {
    test('전화번호가 마스킹되어야 한다', () {
      expect(
        PiiMasker.maskText('연락처는 010-1234-5678 입니다'),
        '연락처는 [전화번호 마스킹] 입니다',
      );
    });

    test('하이픈 없는 전화번호도 마스킹되어야 한다', () {
      expect(
        PiiMasker.maskText('번호 01012345678 알려드립니다'),
        '번호 [전화번호 마스킹] 알려드립니다',
      );
    });

    test('이메일 주소가 마스킹되어야 한다', () {
      expect(
        PiiMasker.maskText('메일은 test@example.com 으로 보내주세요'),
        '메일은 [이메일 마스킹] 으로 보내주세요',
      );
    });

    test('계좌번호가 마스킹되어야 한다', () {
      expect(
        PiiMasker.maskText('계좌 110-123-456789 으로 이체해주세요'),
        '계좌 [계좌번호 마스킹] 으로 이체해주세요',
      );
    });

    test('주민번호가 마스킹되어야 한다', () {
      expect(
        PiiMasker.maskText('주민번호 960115-1234567 확인바랍니다'),
        '주민번호 [주민번호 마스킹] 확인바랍니다',
      );
    });

    test('PII가 없는 일반 텍스트는 변경되지 않아야 한다', () {
      const normal = '오늘 날씨 정말 좋네요!';
      expect(PiiMasker.maskText(normal), normal);
    });

    test('한 문장에 여러 PII가 있을 때 모두 마스킹되어야 한다', () {
      final result = PiiMasker.maskText(
        '전화 010-9999-8888, 메일 abc@domain.kr 로 주세요',
      );
      expect(result.contains('[전화번호 마스킹]'), isTrue);
      expect(result.contains('[이메일 마스킹]'), isTrue);
      expect(result.contains('010-9999-8888'), isFalse);
      expect(result.contains('abc@domain.kr'), isFalse);
    });
  });
}
