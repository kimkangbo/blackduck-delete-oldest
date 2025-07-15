@echo off
rem (c) 2024 AhnLab, Inc. All rights reserved worldwide.

echo compileing and making jar file...
call mvn clean package

echo running...
call java -jar target\DeleteVersion-1.0.0-jar-with-dependencies.jar ^
--blackduck.url=https://blackduck_hub_url/ ^
--blackduck.api.token=Ask to OSS member ^
--detect.project.name="Project Name" ^
--detect.project.version.name="Project Version" ^
--sort.mode=oldest ^
--comments.no_delete=no_delete