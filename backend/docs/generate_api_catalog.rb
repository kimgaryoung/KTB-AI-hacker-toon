require "yaml"
require "json"

ROOT = File.expand_path(__dir__)
SPEC = YAML.load_file(File.join(ROOT, "openapi.yaml"))
SCHEMAS = SPEC.dig("components", "schemas") || {}
HTTP_METHODS = %w[get post put patch delete].freeze

def resolve(schema)
  return {} unless schema.is_a?(Hash)
  ref = schema["$ref"]
  return schema unless ref
  SCHEMAS.fetch(ref.split("/").last)
end

def sample_for(schema, name = nil, depth = 0)
  return nil if depth > 8
  schema = resolve(schema)
  return schema["example"] if schema.key?("example")
  return schema["default"] if schema.key?("default")
  return schema["enum"].first if schema["enum"]
  return sample_for(schema["oneOf"].first, name, depth + 1) if schema["oneOf"]
  return sample_for(schema["allOf"].first, name, depth + 1) if schema["allOf"]

  case schema["type"]
  when "object", nil
    properties = schema["properties"] || {}
    properties.to_h { |key, value| [key, sample_for(value, key, depth + 1)] }
  when "array"
    [sample_for(schema["items"], name, depth + 1)]
  when "integer", "number"
    schema["minimum"] || (name&.downcase&.include?("score") ? 75 : 1)
  when "boolean"
    false
  when "string"
    return "0198c8a7-7f49-7a35-b7a7-8e81b4db0281" if name&.downcase&.end_with?("id")
    return "2026-08-19T06:20:00Z" if schema["format"] == "date-time" || name&.downcase&.end_with?("at")
    return "2026-08-17" if schema["format"] == "date" || name&.downcase&.include?("date")
    return "https://example.com/resource" if schema["format"] == "uri" || name&.downcase&.end_with?("url")
    return "홍길동" if name == "name" || name&.downcase&.end_with?("name")
    return "요청 처리에 필요한 예시 값입니다." if name&.downcase&.include?("message")
    "example"
  else
    nil
  end
end

def type_label(schema)
  nullable = schema["nullable"] ? " | null" : ""
  schema = resolve(schema)
  base = if schema["enum"]
           schema["enum"].map { |v| "`#{v}`" }.join(" \| ")
         elsif schema["type"] == "array"
           "array<#{type_label(schema["items"])}>"
         elsif schema["format"]
           schema["format"]
         else
           schema["type"] || "object"
         end
  base.to_s + nullable
end

def field_rows(schema, prefix = "", required = [], depth = 0)
  return [] if depth > 4
  schema = resolve(schema)
  properties = schema["properties"] || {}
  own_required = schema["required"] || required
  rows = []
  properties.each do |name, child|
    path = prefix.empty? ? name : "#{prefix}.#{name}"
    resolved = resolve(child)
    description = child["description"] || resolved["description"] || "응답에 포함되는 #{name} 값"
    constraints = []
    constraints << "#{child["minimum"]}~#{child["maximum"]}" if child.key?("minimum") && child.key?("maximum")
    constraints << "최대 #{child["maxLength"]}자" if child["maxLength"]
    description += " (#{constraints.join(", ")})" unless constraints.empty?
    rows << [path, type_label(child), own_required.include?(name) ? "Y" : "N", description]
    rows.concat(field_rows(resolved, path, resolved["required"] || [], depth + 1)) if resolved["type"] == "object"
  end
  rows
end

def json_schema_from(content)
  return nil unless content.is_a?(Hash)
  media = content["application/json"] || content.values.first
  media && media["schema"]
end

def resolve_response(response)
  return {} unless response.is_a?(Hash)
  ref = response["$ref"]
  return response unless ref
  SPEC.dig("components", "responses", ref.split("/").last) || response
end

def table(rows, headers)
  return "해당 없음\n" if rows.empty?
  output = "| #{headers.join(" | ")} |\n|#{headers.map { "---" }.join("|")}|\n"
  rows.each { |row| output << "| #{row.map { |v| v.to_s.gsub("|", "\\|").gsub("\n", " ") }.join(" | ")} |\n" }
  output
end

def code_json(value)
  "```json\n#{JSON.pretty_generate(value)}\n```\n"
end

lines = []
lines << "# 관계온도 API 상세 명세서\n"
lines << "> 버전: v1.2  \n> 기준일: 2026-08-19  \n> Base URL: `/api/v1`  \n> 계약 원본: [`openapi.yaml`](./openapi.yaml)\n"
lines << "## 문서 사용법\n"
lines << "이 문서는 프론트엔드, 백엔드, QA가 별도 해석 없이 사용할 수 있도록 **API 한 개당 하나의 독립 명세**로 구성했습니다. 각 API에는 용도, 요청 위치별 필드, 요청 예시, 성공 응답 예시, 응답 필드 설명과 오류 상태를 포함합니다. 예시 UUID와 시각은 형식 설명용이며 실제 값은 요청마다 달라집니다.\n"
lines << "## 공통 요청 규칙\n"
lines << "- 로그인 API를 제외한 보호 API는 `rt_session` 쿠키를 사용합니다.\n- 상태 변경 요청은 `X-CSRF-Token` 헤더가 필요합니다.\n- 생성·분석·메시지 전송 요청은 `Idempotency-Key` 사용을 권장합니다.\n- 모든 응답에는 `X-Request-Id`가 반환됩니다.\n- 일시는 ISO 8601 UTC, 리소스 ID는 UUID 문자열입니다.\n"
lines << "## 공통 오류 응답\n"
lines << code_json({"error"=>{"code"=>"INVALID_REQUEST","message"=>"요청 값을 확인해 주세요.","requestId"=>"req_01J5P8K9W8G0H7P9T0W1K2J3M4","fields"=>[{"field"=>"name","reason"=>"REQUIRED"}]}})

operations = []
SPEC.fetch("paths").each do |path, path_item|
  path_item.each do |method, operation|
    operations << [method, path, operation, path_item] if HTTP_METHODS.include?(method)
  end
end

lines << "## API 목록\n"
lines << table(operations.each_with_index.map { |(method, path, operation, _), index| [index + 1, method.upcase, "`/api/v1#{path}`", operation["summary"]] }, ["No.", "Method", "Endpoint", "설명"])

operations.each_with_index do |(method, path, operation, path_item), index|
  lines << "## #{index + 1}. #{operation["summary"]}\n"
  lines << "`#{method.upcase} /api/v1#{path}`\n"
  lines << "### 어떤 API인가요?\n"
  lines << "#{operation["description"] || operation["summary"]}.\n"
  lines << "### 인증 및 주요 헤더\n"
  header_rows = []
  header_rows << ["Cookie", "string", operation["security"] == [] ? "N" : "Y", "로그인 세션 `rt_session`"]
  header_rows << ["X-CSRF-Token", "string", %w[post put patch delete].include?(method) ? "Y" : "N", "상태 변경 요청 위조 방지 토큰"] if %w[post put patch delete].include?(method)
  header_rows << ["Idempotency-Key", "string", "N", "중복 생성 방지를 위한 멱등 키"] if %w[post].include?(method)
  lines << table(header_rows, ["헤더", "타입", "필수", "설명"])

  parameters = Array(path_item["parameters"]) + Array(operation["parameters"])
  parameter_rows = parameters.map do |param|
    [param["name"], param["in"], type_label(param["schema"] || {}), param["required"] ? "Y" : "N", param["description"] || "#{param["name"]} 값"]
  end
  lines << "### Path·Query 요청값\n"
  lines << table(parameter_rows, ["이름", "위치", "타입", "필수", "설명"])

  request_body = operation["requestBody"]
  request_schema = request_body && json_schema_from(request_body["content"])
  lines << "### Request Body\n"
  if request_schema
    if request_body.dig("content", "multipart/form-data")
      lines << "요청 형식: `multipart/form-data`\n\n"
      lines << table(field_rows(request_schema), ["필드", "타입", "필수", "설명"])
    else
      lines << "요청 형식: `application/json`\n\n"
      lines << table(field_rows(request_schema), ["필드", "타입", "필수", "설명"])
      lines << "#### 요청 JSON 예시\n"
      lines << code_json(sample_for(request_schema))
    end
  else
    lines << "요청 본문 없음. Path 또는 Query 값만 사용합니다.\n"
  end

  success = operation.fetch("responses").find { |status, _| status.to_s.start_with?("2") || status.to_s.start_with?("3") }
  lines << "### 성공 Response\n"
  if success
    status, response = success
    response = resolve_response(response)
    lines << "HTTP `#{status}` — #{response["description"]}\n\n"
    response_schema = json_schema_from(response["content"])
    if response_schema
      lines << "#### 응답 JSON 예시\n"
      lines << code_json(sample_for(response_schema))
      lines << "#### 응답 필드 설명\n"
      lines << table(field_rows(response_schema), ["필드", "타입", "필수", "설명"])
    elsif response["content"]&.key?("text/event-stream")
      lines << "응답은 JSON 문서가 아니라 `text/event-stream` 스트림입니다. 각 이벤트의 `data` 값은 JSON입니다.\n"
    else
      lines << "응답 본문 없음.\n"
    end
  end

  error_rows = operation.fetch("responses").reject { |status, _| status.to_s.start_with?("2") || status.to_s.start_with?("3") }.map do |status, response|
    response = resolve_response(response)
    [status, response["description"] || "공통 오류 응답 형식 참고"]
  end
  lines << "### 오류 Response\n"
  lines << table(error_rows, ["HTTP", "발생 조건"])
  lines << "---\n"
end

output = File.join(ROOT, "API_ENDPOINT_CATALOG.md")
File.write(output, lines.join("\n"))
puts "generated #{output} (#{operations.length} APIs)"
