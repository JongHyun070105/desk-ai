/// 대충톡 PII 마스킹 로직 (PiiMasker.kt의 Dart 포팅)
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

  /// 학습 불가능한 메시지 패턴 (PiiMasker.kt와 동기화)
  static final _nonLearnablePatterns = [
    RegExp(r'^메시지(를|가) 삭제(했습니다|되었습니다)\.?$'),
    RegExp(r'^사진$'),
    RegExp(r'^사진 \d+장$'),
    RegExp(r'^동영상$'),
    RegExp(r'^동영상 \d+개$'),
    RegExp(r'^파일: .+$'),
    RegExp(r'^이모티콘$'),
    RegExp(r'^\(이모티콘\)$'),
    RegExp(r'^스티커$'),
    RegExp(r'^(음성|영상)통화.*$'),
    RegExp(r'^통화시간 .+$'),
    RegExp(r'^부재중 (음성|영상)통화$'),
    RegExp(r'^(송금|입금).*$'),
    RegExp(r'^.+님이 들어왔습니다\.?$'),
    RegExp(r'^.+님이 나갔습니다\.?$'),
    RegExp(r'^.+님을 초대했습니다\.?$'),
    RegExp(r'^.+님이 .+님을 초대했습니다\.?$'),
    RegExp(r'^채팅방 관리자가.*$'),
    RegExp(r'^오픈채팅봇$'),
    RegExp(r'^(지도|위치)$'),
    RegExp(r'^라이브톡.*$'),
    RegExp(r'^(투표|일정|공지)$'),
    RegExp(r'^카카오페이.*$'),
    RegExp(r'^삭제된 메시지입니다\.?$'),
  ];

  /// 학습 불가능한 메시지인지 판별 및 마스킹
  static String maskText(String text) {
    final trimmed = text.trim();

    // 1. 학습 불가능한 전체 메시지 패턴 매칭
    for (final pattern in _nonLearnablePatterns) {
      if (pattern.hasMatch(trimmed)) {
        return '[학습 불가 데이터]';
      }
    }

    // 2. 일반 텍스트 내의 개인정보 마스킹 처리
    var masked = text;
    masked = masked.replaceAll(_rrnRegex, '[주민번호 마스킹]');
    masked = masked.replaceAll(_phoneRegex, '[전화번호 마스킹]');
    masked = masked.replaceAll(_accountRegex, '[계좌번호 마스킹]');
    masked = masked.replaceAll(_emailRegex, '[이메일 마스킹]');
    masked = masked.replaceAll(_cardRegex, '[카드번호 마스킹]');
    return masked;
  }
}
