/// Auto-Me PII 마스킹 로직 (PiiMasker.kt의 Dart 포팅)
///
/// Android 네이티브 PiiMasker.kt와 동일한 정규표현식을 사용하여
/// Dart 환경에서 테스트 가능하도록 포팅한 클래스입니다.
class PiiMasker {
  // 주민/외국인번호
  static final _rrnRegex = RegExp(
    r'\b(?:[0-9]{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[1,2][0-9]|3[0,1]))-?[1-4][0-9]{6}\b',
  );
  // 휴대전화번호
  static final _phoneRegex = RegExp(
    r'\b01[016789]-?[0-9]{3,4}-?[0-9]{4}\b',
  );
  // 계좌번호
  static final _accountRegex = RegExp(
    r'\b\d{3,6}-\d{2,6}-\d{3,6}\b',
  );
  // 이메일 주소
  static final _emailRegex = RegExp(
    r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b',
  );
  // 카드 번호 (13~16자리)
  static final _cardRegex = RegExp(
    r'\b(?:\d[ -]*?){13,16}\b',
  );

  static String maskText(String text) {
    var masked = text;
    masked = masked.replaceAll(_rrnRegex, '[주민번호 마스킹]');
    masked = masked.replaceAll(_phoneRegex, '[전화번호 마스킹]');
    masked = masked.replaceAll(_accountRegex, '[계좌번호 마스킹]');
    masked = masked.replaceAll(_emailRegex, '[이메일 마스킹]');
    masked = masked.replaceAll(_cardRegex, '[카드번호 마스킹]');
    return masked;
  }
}
