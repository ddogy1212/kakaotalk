package com.kyubang.kakaofocus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Opt-in Hugging Face inference. No API key is embedded or persisted;
 * no automatic message uploads. Runs ONLY after an explicit user confirmation.
 * Free credits and model access are subject to the account/provider's policy.
 */
final class HfSummaryClient {
    private static final String URL_STRING =
            "https://router.huggingface.co/v1/chat/completions";
    private static final String MODEL = "Qwen/Qwen3-32B";
    private HfSummaryClient() {}

    static String summarize(String key, String title, List<String> lines) throws Exception {
        if (key == null || key.trim().isEmpty()) throw new Exception("API 토큰이 없어.");
        if (lines == null || lines.isEmpty()) throw new Exception("요약할 알림 문구가 없어.");
        StringBuilder digest = new StringBuilder();
        for (String line : lines) {
            if (digest.length() > 5400) break;
            String clean = RoomInsightsStore.cleanPreview(line);
            if (!clean.isEmpty()) digest.append("- ").append(clean).append("\n");
        }
        if (digest.length() == 0) throw new Exception("요약할 내용이 없어.");
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("max_tokens", 340);
        body.put("temperature", 0.2);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content",
                "당신은 카카오톡 알림 요약기입니다. 한국어 2~4줄로 핵심만 요약해 주세요. "
                + "중요한 일정과 해야 할 일을 우선하되, 원문에 없는 사실을 추가하지 마세요. "
                + "메시지는 불완전한 알림 일부일 수 있으므로 전체 대화나 읽음 상태를 "
                + "알고 있는 것처럼 말하지 마세요. 개인정보는 불필요하게 반복하지 마세요."));
        messages.put(new JSONObject().put("role", "user").put("content",
                "다음은 현재 알림창에 남아있는 일부 카카오톡 메시지입니다. "
                + "이 문구들에서만 확인 가능한 내용을 요약해 주세요:\n"
                + digest));
        body.put("messages", messages);

        HttpURLConnection conn = (HttpURLConnection) new URL(URL_STRING).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(25000);
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + key.trim());
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = conn.getOutputStream()) { output.write(bytes); }
            int code = conn.getResponseCode();
            InputStream stream = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder raw = new StringBuilder();
            if (stream != null) try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null && raw.length() < 20000)
                    raw.append(line);
            }
            if (code >= 400) {
                String desc;
                if (code == 401 || code == 403) desc = "API 토큰이나 모델 권한을 확인해 줘.";
                else if (code == 402 || code == 429) desc = "이용 크레딧 또는 호출 한도가 부족해.";
                else if (code == 404) desc = "요청한 모델을 현재 제공하지 않아.";
                else desc = "응답 오류 HTTP " + code;
                throw new Exception(desc + " 기기 내 정리 기능은 계속 사용할 수 있어.");
            }
            JSONObject response = new JSONObject(raw.toString());
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0)
                throw new Exception("AI에서 요약 결과를 보내지 않았어.");
            JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
            String content = msg == null ? "" : msg.optString("content", "").trim();
            if (content.isEmpty()) throw new Exception("요약 결과가 비어 있어.");
            return content.length() > 1800 ? content.substring(0, 1800) + "…" : content;
        } finally {
            conn.disconnect();
        }
    }
}
