package com.readb.dto.meeting;

/**
 * MultipartFile에서 동기적으로 추출한 byte[] + 메타데이터.
 * 비동기 분석 스레드로 안전하게 전달하기 위함.
 *
 * <p><b>주의 — bytes 필드는 가변(mutable) 배열입니다.</b>
 * record는 객체 자체를 불변으로 만들지만 내부 byte[] 참조까지 보호하지 않습니다.
 * 성능상 큰 오디오 데이터를 복사하지 않고 참조 그대로 전달하므로,
 * <ul>
 *   <li>이 객체를 받은 쪽은 bytes 배열을 절대 수정해서는 안 됩니다(read-only로 취급).</li>
 *   <li>외부에 노출하거나 신뢰 경계를 넘는 곳으로 전달할 때는 {@code Arrays.copyOf} 등으로 복사본을 사용하세요.</li>
 * </ul>
 */
public record RecordingPayload(
        byte[] bytes,
        String originalFilename,
        String contentType
) {}
