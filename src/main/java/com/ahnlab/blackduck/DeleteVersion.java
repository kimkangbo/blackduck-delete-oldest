// (C) 2024 AhnLab, Inc. All rights reserved.
// MIT License

package com.ahnlab.blackduck;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.commons.lang3.tuple.Triple;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
 
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DeleteVersion {
 
    public static void main(String[] args) {
		Map<String, String> params = getPramas(args);
		String BLACK_DUCK_URL = params.getOrDefault("--blackduck.url", "https://blackduck_hub_url/");
		String API_TOKEN = params.getOrDefault("--blackduck.api.token", "");
		String PROJECT_NAME = params.getOrDefault("--detect.project.name", "");
		String VERSION_NAME = params.getOrDefault("--detect.project.version.name", "");
		String SORT_MODE = params.getOrDefault("--sort.mode", "oldest");
		String NO_DELETE = params.getOrDefault("--comments.no_delete", "no_delete");
		
        String bearerToken = getBearerToken(BLACK_DUCK_URL, API_TOKEN);		
        if (bearerToken == null) {
            System.out.println("Failed to obtain bearer token.");
        }
		
		chkAndDelVer(BLACK_DUCK_URL, bearerToken, PROJECT_NAME, VERSION_NAME, SORT_MODE, NO_DELETE);
    }	
	
	private static Boolean chkAndDelVer(String blackDuckUrl, String bearerToken, String projectName, String versionName, String SORT_MODE, String NO_DELETE) {
		Boolean bDelete = false;
		int totalCount = 0;
		int maxCountVers = 10;
		totalCount = getProjectsCount(blackDuckUrl, bearerToken);
		String projectId = getProjectId(blackDuckUrl, projectName, totalCount, bearerToken);
		
		if(projectId != null){
			List<Triple<String, String, String>> verInfoListWithoutNoDelete = getVerInfos(blackDuckUrl, projectId, NO_DELETE, bearerToken, false);
			List<Triple<String, String, String>> verInfoListWithNoDelete = getVerInfos(blackDuckUrl, projectId, NO_DELETE, bearerToken, true);
			
			int countWithoutNoDelete = verInfoListWithoutNoDelete.size();
			int countWithNoDelete = verInfoListWithNoDelete.size();
			int countVers = countWithoutNoDelete + countWithNoDelete;
			
			if(countVers < maxCountVers){
				System.out.println(projectName + "'s version count is " + countVers + "ea. So not delete the " + SORT_MODE + " version." );
			}	
			else if(existTargetVer(versionName, verInfoListWithoutNoDelete)==true || existTargetVer(versionName, verInfoListWithNoDelete)==true){
				System.out.println("# The target version " + versionName + " exists already. So not delete the " + SORT_MODE + " version." );
			}
			else {
				List<Triple<String, String, String>> verInfoList = null;
				if (countWithNoDelete == maxCountVers){
					verInfoList = verInfoListWithNoDelete;
				} else {
					verInfoList = verInfoListWithoutNoDelete;
				}
				Triple<String, String, String> sortedVerInfo = sortVersionInfo(verInfoList, "oldest");
				String oldestVer = sortedVerInfo.getMiddle();
				String createdAt = sortedVerInfo.getRight();
				System.out.println("# Oldest : " + "Version: " + oldestVer + " Created: " + createdAt);
				
				deleteVersion(blackDuckUrl, projectName, projectId, sortedVerInfo, bearerToken);
				deleteScan(blackDuckUrl, projectName, projectId, sortedVerInfo, bearerToken);
				bDelete = true;
			}
		}		
		
		return bDelete;
	}	
	
	private static Map<String, String> getPramas(String[] args) {
		Map<String, String> params = new HashMap<>();
		
		for (String arg : args) {
			String[] keyValue = arg.split("=");
			if(keyValue.length==2){
				params.put(keyValue[0], keyValue[1]);
			}
		}
		return params;
	}
	
    private static String getBearerToken(String blackDuckUrl, String apiToken) {
        String bearerToken = null;
		
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(blackDuckUrl + "/api/tokens/authenticate");
            request.addHeader("Accept", "application/vnd.blackducksoftware.user-4+json");			
            request.addHeader("Authorization", "token " + apiToken);
 
            // Execute the request
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                String jsonResponse = EntityUtils.toString(response.getEntity());
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(jsonResponse);
                bearerToken = rootNode.path("bearerToken").asText();
            } else {
                System.out.println("Failed to authenticate. HTTP error code: " + statusCode);
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("Response body: " + responseBody);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bearerToken;
    }	
 
    private static Integer getProjectsCount(String blackDuckUrl, String bearerToken) {
		int totalCount = 0;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(blackDuckUrl + "/api/projects");
            request.addHeader("Accept", "application/vnd.blackducksoftware.project-detail-7+json");			
            request.addHeader("Authorization", "Bearer " + bearerToken);
 
            // Execute the request
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
//			System.out.println("getProjectsCount Status: " + statusCode);
            if (statusCode == 200) {
                String jsonResponse = EntityUtils.toString(response.getEntity());
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(jsonResponse);
                totalCount = rootNode.path("totalCount").asInt();
				System.out.println("Projects totalCount " + totalCount);				
            } else {
                System.out.println("Failed to authenticate. HTTP error code: " + statusCode);
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("Response body: " + responseBody);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return totalCount;
    }		
 
    private static String getProjectId(String blackDuckUrl, String targetProject, int totalCount, String bearerToken) {
        String projectId = null;
		
		int unit = 1000;
		int loop = totalCount / unit;
		Boolean bFound = false;						
						
		for(int i=0; i<loop+1; i++) {
			int offset = i*unit;		
				
			try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
				HttpGet request = new HttpGet(blackDuckUrl + "/api/projects?limit="+unit+"&offset="+offset);
				request.addHeader("Accept", "application/vnd.blackducksoftware.project-detail-7+json");			
				request.addHeader("Authorization", "Bearer " + bearerToken);
	 
				// Execute the request
				HttpResponse response = httpClient.execute(request);
				int statusCode = response.getStatusLine().getStatusCode();
	//			System.out.println("getProjectId Status: " + statusCode);
				if (statusCode == 200) {
					String jsonResponse = EntityUtils.toString(response.getEntity());
					ObjectMapper objectMapper = new ObjectMapper();
					JsonNode rootNode = objectMapper.readTree(jsonResponse);			
					JsonNode itemsNode = rootNode.path("items");
					if (itemsNode.isArray()) {
						for (JsonNode projectNode : itemsNode) {
							String projectName = projectNode.path("name").asText();
							if(projectName.equals(targetProject) == true){
								projectId = projectNode.path("_meta").path("href").asText();
								projectId = projectId.substring(projectId.lastIndexOf("/") + 1);
								System.out.println("Project Name: " + projectName);						
								bFound = true;
								break;
							} 
						}
					}
				} else {
					System.out.println("Failed to authenticate. HTTP error code: " + statusCode);
					String responseBody = EntityUtils.toString(response.getEntity());
					System.out.println("Response body: " + responseBody);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			if(bFound == false){
				System.out.println("Cannot found " + targetProject);
			}
		}		
		
        return projectId;
    }		
	
    private static String getVersionIdByName(String blackDuckUrl, String targetVersion, String projectId, String bearerToken) {
        String versionId = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(blackDuckUrl + "/api/projects/" + projectId + "/versions");
            request.addHeader("Accept", "application/vnd.blackducksoftware.project-detail-5+json");			
            request.addHeader("Authorization", "Bearer " + bearerToken);
 
            // Execute the request
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
//			System.out.println("getVersionId Status: " + statusCode);
            if (statusCode == 200) {
                String jsonResponse = EntityUtils.toString(response.getEntity());
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(jsonResponse);			
				JsonNode itemsNode = rootNode.path("items");
				if (itemsNode.isArray()) {
					Boolean bFound = false;
                    for (JsonNode versionNode : itemsNode) {
						String versionName = versionNode.path("versionName").asText();
						if(versionName.equals(targetVersion) == true){
							versionId = versionNode.path("_meta").path("href").asText();
							versionId = versionId.substring(versionId.lastIndexOf("/") + 1);
							System.out.println("Version Name: " + versionName + ", Version ID: " + versionId);						
							bFound = true;
							break;
						} 
                    }
					if(bFound == false){
						System.out.println("Cannot found " + targetVersion);
					}
                }
            } else {
                System.out.println("Failed to authenticate. HTTP error code: " + statusCode);
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("Response body: " + responseBody);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return versionId;
    }
	
    private static List<Triple<String, String, String>> getVerInfos(String blackDuckUrl, String projectId, String noDelete, String bearerToken, Boolean bNoDelete) {
		List<Triple<String, String, String>> verInfoList = new ArrayList<>();
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(blackDuckUrl + "/api/projects/" + projectId + "/versions");
            request.addHeader("Accept", "application/vnd.blackducksoftware.project-detail-5+json");			
            request.addHeader("Authorization", "Bearer " + bearerToken);
 
            // Execute the request
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
//			System.out.println("getVersionId Status: " + statusCode);
            if (statusCode == 200) {
                String jsonResponse = EntityUtils.toString(response.getEntity());
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(jsonResponse);			
				JsonNode itemsNode = rootNode.path("items");
				if (itemsNode.isArray()) {
					List<String> versionNameList = new ArrayList<String>();
					List<String> createdAtList = new ArrayList<String>();
					int count = countComments(itemsNode, noDelete);
                    for (JsonNode versionNode : itemsNode) {
						Boolean bCheckedNoDelete = checkComment(versionNode, noDelete);
						if((bCheckedNoDelete==true && bNoDelete==true) || (bCheckedNoDelete==false && bNoDelete==false)){
							String versionId = versionNode.path("_meta").path("href").asText();
							versionId = versionId.substring(versionId.lastIndexOf("/") + 1);						
							String versionName = versionNode.path("versionName").asText();
							String createdAt = versionNode.path("createdAt").asText();
							verInfoList.add(Triple.of(versionId, versionName, createdAt));												
						} 
                    }
                }
            } else {
                System.out.println("Failed to authenticate. HTTP error code: " + statusCode);
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("Response body: " + responseBody);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return verInfoList;
    }	

	private static Boolean existTargetVer(String versionName, List<Triple<String, String, String>> verInfoList){
		Boolean bExist = false;	
		
		for(Triple<String, String, String> verInfo : verInfoList){
			String existVer = verInfo.getMiddle();
			if(versionName.equals(existVer)==true){
				bExist = true;
				break;
			}
		}
		
		return bExist;
	}
	
	private static int countComments(JsonNode itemsNode, String noDelete) {
		int count = 0;
		Boolean bCheck = false;
		for (JsonNode versionNode : itemsNode) {
			bCheck = checkComment(versionNode, noDelete);
			if(bCheck==true){
				count++;
			}			
		}
		return count;
	}
	
	private static Boolean checkComment(JsonNode versionNode, String noDelete) {
		Boolean bCheck = false;
		String comments = versionNode.path("releaseComments").asText();
		comments = comments.toLowerCase();
		if(comments.contains(noDelete)==true){
			bCheck = true;
		}			
		return bCheck;
	}
	
	private static Triple<String, String, String> sortVersionInfo(List<Triple<String, String, String>> verInfoList, String mode) {
		Triple<String, String, String> targetVerInfo = null;
		mode = mode.toLowerCase();
        if(mode.equals("oldest")==false && mode.equals("latest")==false){
			mode = "oldest";
		}
		int i=0;
		for(Triple<String, String, String> verInfo : verInfoList){
			i++;
			System.out.println(i + "th Version: " + verInfo.getMiddle() + ", Created : " + verInfo.getRight());
			if(i==1){
				targetVerInfo = verInfo;
			}else {
				String targetDate = targetVerInfo.getRight();
				String curDate = verInfo.getRight();
				if(mode.equals("oldest")==true) {
					if(targetDate.compareTo(curDate) < 0){
						continue;
					}else {
						targetVerInfo = verInfo;			
					}
				}else if(mode.equals("latest")==true) {
					if(targetDate.compareTo(curDate) > 0){
						continue;
					}else {
						targetVerInfo = verInfo;
					}
				}
			}
		}
		return targetVerInfo;
	}
 
    private static Boolean deleteVersion(String blackDuckUrl, String projectName, String projectId, Triple<String, String, String> verInfo, String bearerToken) {
		String versionId = verInfo.getLeft();
		String versionName = verInfo.getMiddle();
		String createdAt = verInfo.getRight();
		Boolean bDeleted = false;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpDelete request = new HttpDelete(blackDuckUrl + "/api/projects/" + projectId + "/versions/" + versionId);            
            request.addHeader("Authorization", "Bearer " + bearerToken);
 
            // Execute the request
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
//			System.out.println("deleteVersion Status: " + statusCode);
            if (statusCode == 204) {
				System.out.println("# Deleted project: " + projectName + ", version: " + versionName + " created at " + createdAt);
            } else {
                System.out.println("Failed to authenticate. HTTP error code: " + statusCode);
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("Response body: " + responseBody);
            }
			bDeleted = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bDeleted;
    }	

    private static Integer getScansCount(String blackDuckUrl, String bearerToken) {
		int totalCount = 0;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(blackDuckUrl + "/api/codelocations");
            request.addHeader("Accept", "application/vnd.blackducksoftware.scan-5+json");
            request.addHeader("Authorization", "Bearer " + bearerToken);
 
            // Execute the request
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
//			System.out.println("getScansCount Status: " + statusCode);
            if (statusCode == 200) {
                String jsonResponse = EntityUtils.toString(response.getEntity());
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(jsonResponse);
                totalCount = rootNode.path("totalCount").asInt();
				System.out.println("Scans totalCount " + totalCount);				
            } else {
                System.out.println("Failed to authenticate. HTTP error code: " + statusCode);
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("Response body: " + responseBody);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return totalCount;
    }
	
	private static List<String> getScanIds(String blackDuckUrl, String projectName, String versionName, int totalCount, String bearerToken) {
		// example for scan name: "[test for windows test for scan_windows_online.bat 02] verify signature"
		List<String> scanIdList = new ArrayList<>();
		String scanNameChk = "[" + projectName + " " + versionName + "]";
		int unit = 1000;
		int loop = totalCount / unit;
		Boolean bFound = false;						
						
		for(int i=0; i<loop+1; i++) {
			int offset = i*unit;
			try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
//				System.out.println("getScanIds unit: " + unit + ", offset: " + offset);				
				HttpGet request = new HttpGet(blackDuckUrl + "/api/codelocations?limit="+unit+"&offset="+offset);				
				request.addHeader("Accept", "application/vnd.blackducksoftware.scan-5+json");			
				request.addHeader("Authorization", "Bearer " + bearerToken);
	 
				// Execute the request
				HttpResponse response = httpClient.execute(request);
				int statusCode = response.getStatusLine().getStatusCode();
//				System.out.println("getScanIds Status: " + statusCode);				
				if (statusCode == 200) {
					String jsonResponse = EntityUtils.toString(response.getEntity());
					ObjectMapper objectMapper = new ObjectMapper();
					JsonNode rootNode = objectMapper.readTree(jsonResponse);			
					JsonNode itemsNode = rootNode.path("items");
					if (itemsNode.isArray()) {
						for (JsonNode scanNode : itemsNode) {
							if(checkName(scanNode, scanNameChk) == true){
								String scanId = scanNode.path("_meta").path("href").asText();
								scanId = scanId.substring(scanId.lastIndexOf("/") + 1);
								scanIdList.add(scanId);						
							}
						}
					}
				} else {
					System.out.println("Failed to authenticate. HTTP error code: " + statusCode);
					String responseBody = EntityUtils.toString(response.getEntity());
					System.out.println("Response body: " + responseBody);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}			
		}	
		
		if(scanIdList.size() == 0){
			System.out.println("Cannot found the scan Ids for " + projectName + ", " + versionName);
		}		
		
		return scanIdList;
	}
	
	private static Boolean checkName(JsonNode scanNode, String scanNameChk) {
		Boolean bCheck = false;
		String name = scanNode.path("name").asText();
		if(name.startsWith(scanNameChk)==true){
			bCheck = true;
			System.out.println("Target scans to delete: " + name);			
		}			
		return bCheck;
	}	
	
    private static Integer deleteScanId(String blackDuckUrl, List<String> scanIdList, String bearerToken) {
		int totalCount = 0;
		int deletedCount = 0;
		
		for(String scanId : scanIdList) {
			try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
				HttpDelete request = new HttpDelete(blackDuckUrl + "/api/codelocations/" + scanId);
				request.addHeader("Authorization", "Bearer " + bearerToken);
	 
				// Execute the request
				HttpResponse response = httpClient.execute(request);
				int statusCode = response.getStatusLine().getStatusCode();
	//			System.out.println("getScansCount Status: " + statusCode);
				if (statusCode == 204) {
					System.out.println("Scan Deleted: " + scanId);	
					deletedCount++;
				} else {
					System.out.println("Failed to authenticate. HTTP error code: " + statusCode);
					String responseBody = EntityUtils.toString(response.getEntity());
					System.out.println("Response body: " + responseBody);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return deletedCount;
    }	
	
    private static Integer deleteScan(String blackDuckUrl, String projectName, String projectId, Triple<String, String, String> verInfo, String bearerToken) {
		String versionId = verInfo.getLeft();
		String versionName = verInfo.getMiddle();
		
        int totalCount = getScansCount(blackDuckUrl, bearerToken);
		List<String> scanIdList = getScanIds(blackDuckUrl, projectName, versionName, totalCount, bearerToken);
		int deletedCount = deleteScanId(blackDuckUrl, scanIdList, bearerToken);

        return deletedCount;
    }		

}
