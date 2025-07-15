개발 목적
========
Black Duck 프로젝트의 버전은 최대 10개까지만 저장 가능합니다.   
최대 10개가 등록된 경우, 수동으로 1개를 삭제 후 추가가 가능합니다.   
CI/CD 에서 제품의 특정 버전으로 결과를 Black Duck에 등록 시, 기존에 10개의 버전이 존재하는 경우 추가 등록이 안됩니다.   
   
Black Duck scan 진행 시, 다음과 같이 version이 10개가 넘었다는 경고가 보여지고 scan이 중지됩니다.   

	2024-08-06 12:36:12 KST INFO  [main] --- ======== Detect Issues ========
	2024-08-06 12:36:12 KST INFO  [main] ---
	2024-08-06 12:36:12 KST INFO  [main] --- EXCEPTIONS:
	2024-08-06 12:36:12 KST INFO  [main] ---        Create or Locate Project
	2024-08-06 12:36:12 KST INFO  [main] ---                Licensing issue: 'Number of versions per project' is beyond its licensed limit (Actual value: 11 | Licensed value: 10). [HTTP Error]: There was a problem trying to POST https://osas.ahnlab.com/api/projects/59bd5a1f-8cba-40d8-b77c-5173f8db2fec/versions, response was 402 Payment Required.

이에 다음과 같은 조건에서 동작하는 oldest 혹은 latest 버전 자동삭제 프로그램입니다.
1. 프로젝트 버전이 10개이고 comment에 no_delete 문자열이 적혀 있지 않은 버전 중에 oldest version을 삭제합니다.
2. 프로젝트 버전이 10개이고 10개의 버전에 comment에 no_delete 문자열이 적혀 있는 경우 그 중에 oldest version을 삭제합니다.
3. 만약 1, 2번에 해당하지만 타겟 버전(검증 결과를 추가할 버전)이 기존 버전에 존재하는 경우, 해당 버전은 삭제하지 않습니다.
   
프로그램 구동 환경   
================
jre 8.0~17.0 에서 구동됩니다.   
      
      
프로그램 인터페이스   
================
프로그램 옵션 정의   
---------------------
jar 파일로 개발하며 입력 인터페이스는 다음과 같이 정의합니다.   

	--blackduck.url	https://blackduck_hub_url
	--blackduck.api.token	black duck 접속 토큰 값
	--detect.project.name	프로젝트 명
	--detect.project.version.name  프로젝트에 결과를 저장할 버전 명
	--comments.no_delete	버전 설명문에 작성된 버전 삭제 금지 문자열 (예, no_delete)
	--sort.mode	삭제 버전 기준 (oldest or latest)
	
프로그램 빌드 방법  
=======================
1. JDK를 설치합니다.   
2. run_BlackduckDeleteVersion.bat 을 실행합니다.
         
프로그램 설치 및 실행 방법   
=======================
Windows 경우   
---------------------
--blackduck.api.token는 OSS 담당자에게 요청합니다.   

	>java -jar DeleteVersion-1.0.0-jar-with-dependencies.jar ^
	--blackduck.url=https://blackduck_hub_url ^
	--blackduck.api.token="Ask to OSS Member" ^
	--detect.project.name=ProjectName ^
	--detect.project.version.name=ProjectVersion  ^
	--sort.mode=oldest ^
	--comments.no_delete=no_delete
	
Linux 경우   
---------------------
--blackduck.api.token는 OSS 담당자에게 요청합니다.   

	$java -jar DeleteVersion-1.0-SNAPSHOT-jar-with-dependencies.jar \
	--blackduck.url="https://blackduck_hub_url" \
	--blackduck.api.token="Ask to OSS Member" \
	--detect.project.name="ProjectName" \
	--detect.project.version.name=ProjectVersion \
	--sort.mode="oldest" \
	--comments.no_delete="no_delete"
	
Copyright
=========

(C) 2024 AhnLab, Inc. All rights reserved.	