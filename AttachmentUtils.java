package egovframework.ag.common.utils;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.ImageIcon;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.imgscalr.Scalr;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

import egovframework.ag.common.vo.FileInfoVo;

public class AttachmentUtils {

	private final static Logger logger = LogManager.getLogger();

	public static String getUploadPath(String path) throws Exception {
		String uploadPath = "D://spring/workspace/egovupload/";
		try {
			uploadPath += path + "/";
		}catch(Exception e){
			logger.debug("Exception getUploadPath : " + e);
			
		}
		return uploadPath;
	}

	public static String getUploadUrl(String path) throws Exception {
		String uploadUrl = "/egovupload/";
		try {
			uploadUrl += path + "/";
		}catch(Exception e){
			logger.debug("Exception getUploadUrl : " + e);
			
		}
		return uploadUrl;
	}
	/*
	 * 파일 다운로드 실행
	 * ForumController.download() 메소드에서 호출.
	 */
	public static void download(HttpServletRequest request,
							HttpServletResponse response, String path) throws Exception {

		String fileName = request.getParameter("file");
		String fileName2 = URLEncoder.encode(fileName, "UTF-8");
		File file = new File(path, fileName);
		if (!file.exists()) {
			throw new FileNotFoundException("File for download does not exist.");
		}
        InputStream in = null;
        OutputStream out = null;
        try {
        	in = new BufferedInputStream(new FileInputStream(file));
        	out = new BufferedOutputStream(response.getOutputStream());
    		response.setContentType("application/octet-stream");
    		response.setHeader("Content-Transfer-Encoding:", "base64");
    		response.setHeader("Content-Disposition", "attachment;filename=" + fileName2 + ";");
    		response.setContentLength((int)file.length());
    		byte b[] = new byte[1024];
			int numRead = 0;
			out = response.getOutputStream();
			while ((numRead = in.read(b)) != -1) {
				out.write(b, 0, numRead);
			}
			out.flush();
			return;
        } finally {
        	in.close();
   			out.close();
     	}
	}

	public static FileInfoVo fileUpload(MultipartFile multiFile, String uploadPath, String saveType, long maxFileSize) {
		String uploadSubPath = DateUtils.getSysDate("yyyy") + "/" + DateUtils.getSysDate("MM") + "/" + DateUtils.getSysDate("dd") + "/";
		uploadPath += uploadSubPath;

		FileInfoVo fileInfoVo = new FileInfoVo();
		fileInfoVo.setFilePath(uploadPath);
		fileInfoVo.setOrgFileName(multiFile.getOriginalFilename());
		fileInfoVo.setFileSize(multiFile.getSize());
		fileInfoVo.setFileContentType(multiFile.getContentType());

		try {
			String contentType = multiFile.getContentType();
			long fileSize = multiFile.getSize();


			if (saveType != null) {
				if (checkFileType(saveType, contentType) == false) {
					fileInfoVo.setResult(false);
					return fileInfoVo;
				}
			}


			if(maxFileSize > 0 && maxFileSize < fileSize) {
				fileInfoVo.setResult(false);
				return fileInfoVo;
			}

			String fileName = AttachmentUtils.fileMakeName(uploadPath, multiFile.getOriginalFilename());

			File transfer = new File(uploadPath + fileName);

			multiFile.transferTo(transfer);

			fileInfoVo.setResult(true);
			fileInfoVo.setUploadFileName(uploadSubPath + fileName);

		}catch(Exception e) {
			fileInfoVo.setResult(false);
		}

		return fileInfoVo;
	}
	
	public static Map<String, Object> fileUpload(HttpServletRequest request, String uploadPath, 
			String attachFile, String attachUrl, Long maxSize, String saveType, String fileDir, String attachMask) throws Exception {
		MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
		MultipartFile multiFile = multipartRequest.getFile(attachFile);
		Map<String, Object> mapFile = new HashMap<String, Object>();
		String fileMask = null;
		String fileName = null;
		String fileType = null;
		Long fileSize = null;
		Integer fileWidth = 0;
		Integer fileHeight = 0;
		Integer fileOrientation = 1;
		String chkSize = "Y";
		String chkType = "Y";
		String chkExtension = "Y";
		String returnType = "FAIL";

		String sessionId = "guest";
		Metadata metadata = null;
		Directory directory = null;

		try{
			if(ReferenceUtils.getPrincipalForId()!=null){
				sessionId = ReferenceUtils.getPrincipalForId();
			}

			if (multiFile!= null && multiFile.getSize() > 0) {
				fileType = multiFile.getContentType();
				fileSize = multiFile.getSize();
				
				String origianlFile = multiFile.getOriginalFilename();
				int dot = origianlFile.indexOf("hwp");
		        if (dot != -1) {
		        	fileType = "application/hwp";
		        }
				
				if(maxSize!=null && maxSize<fileSize) {
					chkSize = "N";
					returnType = "OVERSIZE";
				}
				// MimeType 제한 확인
				if(saveType!=null && saveType.length()>0 && !checkFileType(saveType, fileType)){
					chkType = "N";
					returnType = "WRONGTYPE";
				}
				
				if(saveType!=null && saveType.length()>0 && !checkFileExtension(saveType, multiFile.getOriginalFilename())){
					chkExtension = "N";
					returnType = "WRONGEXTENSION";
				}
				
				if(chkSize.equals("Y") && chkType.equals("Y") && chkExtension.equals("Y")) {
					if(attachUrl!=null && !attachUrl.equals("")){
						AttachmentUtils.fileDelete(uploadPath, new String[]{attachUrl});
					}
	
					if(attachMask!=null && !attachMask.equals("")){
						fileMask = attachMask;
					}
					else{
						fileMask = multiFile.getOriginalFilename();
					}
					
					fileName = AttachmentUtils.fileMakeName(uploadPath, multiFile.getOriginalFilename());
					File transfer = new File(uploadPath + fileName);

					multiFile.transferTo(transfer);
					
					//이미지일 경우만 추출
					if(fileType!=null && fileType.length()>0 && checkFileType("F1", fileType)){
						// 파일 가로,세로 길이
						Image img = new	ImageIcon(uploadPath + fileName).getImage();
						if(img!=null){
							fileWidth = img.getWidth(null);
							fileHeight = img.getHeight(null);
						}
	
						// 회전각
						File imgFile = new File(uploadPath + fileName);
					    metadata = ImageMetadataReader.readMetadata(imgFile);
					    if( metadata != null ){
					    	directory = metadata.getDirectory(ExifIFD0Directory.class);
					    }
					    if( directory != null  && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)){
					    	fileOrientation = directory.getInt( ExifIFD0Directory.TAG_ORIENTATION );
					    }
					}

					returnType = "SUCCESS";
				}
			}			
		}catch(Exception e){
			logger.debug("Exception fileUpload : " + e);
			
		}
		mapFile.put("fileMask", fileMask);
		mapFile.put("fileName", fileName);
		mapFile.put("fileDir", fileDir);
		mapFile.put("fileType", fileType);
		mapFile.put("fileSize", fileSize);
		mapFile.put("fileWidth", fileWidth);
		mapFile.put("fileHeight", fileHeight);
		mapFile.put("fileOrientation", fileOrientation);
		mapFile.put("registerId", sessionId);
		mapFile.put("returnType", returnType);

		return mapFile;
	}
	
	public static Map<String, Object> fileUploadCkeditor(HttpServletRequest request, MultipartFile multiFile,  
			String uploadPath, Long maxSize, String saveType, String fileDir) throws Exception {

		Map<String, Object> mapFile = new HashMap<String, Object>();
		String fileMask = null;
		String fileName = null;
		String fileType = null;
		Long fileSize = null;
		String chkSize = "Y";
		String chkType = "Y";
		String chkExtension = "Y";
		String returnType = "FAIL";

		try{

			if (multiFile!= null && multiFile.getSize() > 0) {
				fileType = multiFile.getContentType();
				fileSize = multiFile.getSize();
				
				if(maxSize!=null && maxSize<fileSize) {
					chkSize = "N";
					returnType = "OVERSIZE";
				}
				// MimeType 제한 확인
				if(saveType!=null && saveType.length()>0 && !checkFileType(saveType, fileType)){
					chkType = "N";
					returnType = "WRONGTYPE";
				}
				
				if(saveType!=null && saveType.length()>0 && !checkFileExtension(saveType, multiFile.getOriginalFilename())){
					chkExtension = "N";
					returnType = "WRONGEXTENSION";
				}
				
				if(chkSize.equals("Y") && chkType.equals("Y") && chkExtension.equals("Y")) {
					fileMask = multiFile.getOriginalFilename();
					fileName = fileMakeName(uploadPath, multiFile.getOriginalFilename());
					File transfer = new File(uploadPath + fileName);

					multiFile.transferTo(transfer);
					returnType = "SUCCESS";
				}
			}			
		}catch(Exception e){
			logger.debug("Exception fileUploadCkeditor : " + e);
			
		}
		mapFile.put("fileMask", fileMask);
		mapFile.put("fileName", fileName);
		mapFile.put("returnType", returnType);

		return mapFile;
	}
	
	public static void fileDelete(String uploadPath, String[] fileNames) throws Exception {
		try{
			for (int i = 0; i < fileNames.length; i++) {
				logger.debug("DeleteFiles(" + i + ") : " + uploadPath + fileNames[i]);
				File file = new File(uploadPath, fileNames[i]);
				if (file.exists()) {
					file.delete();
				}
			}
		}catch(Exception e){
			logger.debug("Exception fileDelete : " + e);
			
		}
	}

	public static String fileMakeName(String uploadPath, String fileName) throws Exception{
        String fileBody = null;
        String fileExt = null;
		String newFileName = null;
		try{
			File f1 = new File(uploadPath);
			if(!f1.isDirectory()){
				f1.mkdirs();
			}
	        int dot = fileName.lastIndexOf(".");
	        if (dot != -1) {
	          fileBody = fileName.substring(0, dot);
	          fileExt  = fileName.substring(dot);  // includes "."
	        }
	        else {
	          fileBody = fileName;
	          fileExt = "";
	        }
	        UUID randUUID =UUID.randomUUID();
	        fileBody = randUUID.toString();
	
			newFileName	= fileBody +  fileExt;

		}catch(Exception e){
			logger.debug("Exception fileMakeName : " + e);
			
		}
        return newFileName;
	}
	
	public static Long fileSize(String uploadPath, String uploadUrlFull, String fileName) throws Exception {
		Long fileSize = Long.valueOf(0);
		try{
			File file = new File(uploadPath, fileName);
			if (file.exists()) {
				URL location = new URL(uploadUrlFull + fileName);
				URLConnection urlCon = location.openConnection();
				InputStream inputStream = urlCon.getInputStream();
				fileSize = Long.valueOf(inputStream.available());				   
				
				fileSize = file.length();				
			}		
		}catch(Exception e){
			logger.debug("Exception fileSize : " + e);
			
		}		
		return fileSize;
	}
	
	/**
	 * 
	 * @param saveType
	 * 	F1 : 이미지
	 * 	F2 : 문서
	 * 	F3 : 플래시
	 * 	F4 : 미디어(영상)
	 * 	F5 : 이미지/문서
	 * 	F6 : 이미지/미디어
	 * 	F7 : 미디어(영상, 음원)
	 * 	F8 : 이미지/문서/플래시/미디어(영상, 음원) 
	 * @param contentType
	 * 	이미지
	 * 		: .gif, .png, .jpg, .bmp
	 * 		: image/gif, image/png, image/x-png, image/jpeg, image/pjpeg, image/bmp 
	 * 	미디어 영상
	 * 		: .swf, .flv, .wmv, mp4, avi
	 * 		: application/x-shockwave-flash, video/x-flv, video/x-ms-wmv, video/mp4, video/x-msvideo
	 * 	미디어 음원
	 * 		: .mp3
	 * 		: audio/mpeg
	 * 	문서
	 * 		: .txt, .xls, .doc, .pdf, .ppt, .hwp, .zip
	 * 		: text/plain, application/vnd.ms-excel, application/msword, application/pdf, application/vnd.ms-powerpoint, application/hwp, application/haansofthwp, application/zip, application/x-zip-compressed 
	 * 	플래시
	 * 		: .swf, .flv
	 * 		: application/x-shockwave-flash, video/x-flv, application/octet-stream 
	 * @return
	 */
	public static boolean checkFileType(String saveType, String contentType){
		boolean fResult = false;
		logger.debug("saveType === " + saveType);
		logger.debug("contentType === " + contentType);
		
		// 이미지
		if(saveType.equals("F1")){
			if(contentType!=null && contentType.length()>0
					&& (contentType.equals("image/gif") || contentType.equals("image/png") || contentType.equals("image/x-png") || contentType.equals("image/jpeg") || contentType.equals("image/pjpeg") || contentType.equals("image/bmp")
							 || contentType.equals("application/force-download") || contentType.equals("application/octet-stream")) ){
				fResult = true;
			}
		// 문서
		}else if(saveType.equals("F2")){
			if(contentType!=null && contentType.length()>0
					&& (contentType.equals("text/plain") || contentType.equals("application/vnd.ms-excel") || contentType.equals("application/msword")
							|| contentType.equals("application/pdf") || contentType.equals("application/vnd.ms-powerpoint")
							|| contentType.equals("application/hwp") || contentType.equals("application/haansofthwp")
							|| contentType.equals("application/zip") || contentType.equals("application/x-zip-compressed")
							|| contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
							|| contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
							|| contentType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")
							|| contentType.equals("application/force-download") || contentType.equals("application/octet-stream"))){
				fResult = true;
			}
		// 플래쉬
		}else if(saveType.equals("F3")){
			if(contentType!=null && contentType.length()>0
					&& (contentType.equals("application/x-shockwave-flash") || contentType.equals("video/x-flv")
							|| contentType.equals("application/force-download") || contentType.equals("application/octet-stream")) ){
				fResult = true;
			}
		// 미디어(영상)
		}else if(saveType.equals("F4")){
			if(contentType!=null && contentType.length()>0
					&& (contentType.equals("video/mp4") || contentType.equals("video/x-flv")
							|| contentType.equals("application/force-download") || contentType.equals("application/octet-stream")) ){
				fResult = true;
			}
		// 이미지/문서
		}else if(saveType.equals("F5")){
			if(contentType!=null && contentType.length()>0
					&& (contentType.equals("image/gif") || contentType.equals("image/png") || contentType.equals("image/x-png") || contentType.equals("image/jpeg") || contentType.equals("image/pjpeg") || contentType.equals("image/bmp")
							|| contentType.equals("text/plain") || contentType.equals("application/vnd.ms-excel") || contentType.equals("application/msword")
							|| contentType.equals("application/pdf") || contentType.equals("application/vnd.ms-powerpoint")
							|| contentType.equals("application/hwp") || contentType.equals("application/haansofthwp")
							|| contentType.equals("application/zip") || contentType.equals("application/x-zip-compressed")
							|| contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
							|| contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
							|| contentType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")
							|| contentType.equals("application/force-download") || contentType.equals("application/octet-stream"))){
				fResult = true;
			}
		// 이미지/미디어
		}else if(saveType.equals("F6")){
			if(contentType!=null && contentType.length()>0
					&& (contentType.equals("image/gif") || contentType.equals("image/png") || contentType.equals("image/x-png") || contentType.equals("image/jpeg") || contentType.equals("image/pjpeg") || contentType.equals("image/bmp")
							|| contentType.equals("application/x-shockwave-flash") || contentType.equals("video/x-flv")
							|| contentType.equals("audio/mpeg") || contentType.equals("audio/mp3") || contentType.equals("video/x-ms-wmv") || contentType.equals("video/mp4")
							|| contentType.equals("application/force-download") || contentType.equals("application/octet-stream"))){
				fResult = true;
			}
		// 미디어(영상, 음원)
		}else if(saveType.equals("F7")){
			if(contentType!=null && contentType.length()>0
					&& (contentType.equals("application/x-shockwave-flash") || contentType.equals("video/x-flv")
							|| contentType.equals("audio/mpeg") || contentType.equals("audio/mp3") || contentType.equals("video/x-ms-wmv") || contentType.equals("video/mp4")
							|| contentType.equals("application/force-download") || contentType.equals("application/octet-stream"))){
				fResult = true;
			}
		// 이미지/문서/플래시/미디어(영상, 음원)
		}else if(saveType.equals("F8")){
			if(contentType!=null && contentType.length()>0
					&& (contentType.equals("image/gif") || contentType.equals("image/png") || contentType.equals("image/x-png") || contentType.equals("image/jpeg") || contentType.equals("image/pjpeg") || contentType.equals("image/bmp")
							|| contentType.equals("text/plain") || contentType.equals("application/vnd.ms-excel") || contentType.equals("application/msword")
							|| contentType.equals("application/pdf") || contentType.equals("application/vnd.ms-powerpoint")
							|| contentType.equals("application/hwp") || contentType.equals("application/haansofthwp")
							|| contentType.equals("application/zip") || contentType.equals("application/x-zip-compressed")
							|| contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
							|| contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
							|| contentType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")
							|| contentType.equals("application/x-shockwave-flash") || contentType.equals("video/x-flv")
							|| contentType.equals("audio/mpeg") || contentType.equals("audio/mp3") || contentType.equals("video/x-ms-wmv") || contentType.equals("video/mp4")
							|| contentType.equals("application/force-download") || contentType.equals("application/octet-stream"))){
				fResult = true;
			}
		// PDF 파일
		}else if(saveType.equals("F9")){
			if(contentType!=null && contentType.length()>0 && contentType.equals("application/pdf")) {
				fResult = true;
			}
		}
		
		return fResult;
	}
	
	public static boolean checkFileExtension(String saveType, String fileName){
		boolean fResult = false;
		String contentExtension = null;
        int dot = fileName.lastIndexOf(".");
        if (dot != -1) {
        	contentExtension  = fileName.substring(dot).toLowerCase();  // includes "."
        }
        else {
        	contentExtension = "";
        }
		logger.debug("saveType === " + saveType);
		logger.debug("contentExtension === " + contentExtension);
		
		// 이미지
		if(saveType.equals("F1")){
			if(contentExtension!=null && contentExtension.length()>0
					&& (contentExtension.equals(".gif") || contentExtension.equals(".jpg") || contentExtension.equals(".jpeg") 
							|| contentExtension.equals(".png") || contentExtension.equals(".bmp") )){
				fResult = true;
			}
		// 문서
		}else if(saveType.equals("F2")){
			if(contentExtension!=null && contentExtension.length()>0
					&& (contentExtension.equals(".txt") || contentExtension.equals(".xls") || contentExtension.equals(".doc")
							|| contentExtension.equals(".pdf") || contentExtension.equals(".ppt")
							|| contentExtension.equals(".hwp") || contentExtension.equals(".zip")
							|| contentExtension.equals(".xlsx") || contentExtension.equals(".docx") || contentExtension.equals(".pptx"))){
				fResult = true;
			}
		// 플래쉬
		}else if(saveType.equals("F3")){
			if(contentExtension!=null && contentExtension.length()>0
					&& (contentExtension.equals(".swf") || contentExtension.equals(".flv")) ){
				fResult = true;
			}
		// 미디어(영상)
		}else if(saveType.equals("F4")){
			if(contentExtension!=null && contentExtension.length()>0
					&& (contentExtension.equals(".mp4") || contentExtension.equals(".flv")) ){
				fResult = true;
			}
		// 이미지/문서
		}else if(saveType.equals("F5")){
			if(contentExtension!=null && contentExtension.length()>0
					&& (contentExtension.equals(".gif") || contentExtension.equals(".jpg") || contentExtension.equals(".jpeg")
							|| contentExtension.equals(".png") || contentExtension.equals(".bmp")
							|| contentExtension.equals(".txt") || contentExtension.equals(".xls") || contentExtension.equals(".doc")
							|| contentExtension.equals(".pdf") || contentExtension.equals(".ppt")
							|| contentExtension.equals(".hwp") || contentExtension.equals(".zip")
							|| contentExtension.equals(".xlsx") || contentExtension.equals(".docx") || contentExtension.equals(".pptx"))){
				fResult = true;
			}
		// 이미지/미디어
		}else if(saveType.equals("F6")){
			if(contentExtension!=null && contentExtension.length()>0
					&& (contentExtension.equals(".gif") || contentExtension.equals(".jpg") || contentExtension.equals(".jpeg")
							|| contentExtension.equals(".png") || contentExtension.equals(".bmp")
							|| contentExtension.equals(".swf") || contentExtension.equals(".mp4") 
							|| contentExtension.equals(".flv") || contentExtension.equals(".wmv"))){
				fResult = true;
			}
		// 미디어(영상, 음원)
		}else if(saveType.equals("F7")){
			if(contentExtension!=null && contentExtension.length()>0
					&& (contentExtension.equals(".swf") || contentExtension.equals(".mp4") || contentExtension.equals(".mp3") 
							|| contentExtension.equals(".flv") || contentExtension.equals(".wmv"))){
				fResult = true;
			}
		// 이미지/문서/플래시/미디어(영상, 음원)
		}else if(saveType.equals("F8")){
			if(contentExtension!=null && contentExtension.length()>0
					&& (contentExtension.equals(".gif") || contentExtension.equals(".jpg") || contentExtension.equals(".jpeg")
							|| contentExtension.equals(".png") || contentExtension.equals(".bmp")
							|| contentExtension.equals(".txt") || contentExtension.equals(".xls") || contentExtension.equals(".doc")
							|| contentExtension.equals(".pdf") || contentExtension.equals(".ppt")
							|| contentExtension.equals(".hwp") || contentExtension.equals(".zip")
							|| contentExtension.equals(".xlsx") || contentExtension.equals(".docx") || contentExtension.equals(".pptx")
							|| contentExtension.equals(".swf") || contentExtension.equals(".mp4") || contentExtension.equals(".mp3") 
							|| contentExtension.equals(".flv") || contentExtension.equals(".wmv"))){
				fResult = true;
			}
		}
		
		return fResult;
	}
	
    public static Map<String, Object> imageResize(String orgFilePath, String targetFilePath, String imageType, int resultWidth, int resultHeight) throws Exception{

		Map<String, Object> mapFile = null;

		BufferedImage inputImage = ImageIO.read(new File(orgFilePath));
    		
        int originWidth = inputImage.getWidth();
        int originHeight = inputImage.getHeight();
        
        if (originWidth >= resultWidth && originHeight >= resultHeight) {
	
        	// Scale in respect to width or height?
	        Scalr.Mode scaleMode = Scalr.Mode.FIT_TO_HEIGHT;
	        int maxSize = resultHeight;
	        
	        // 가로 이미지 맞췄을 때의 세로 길이
	        double optHeight = originHeight * ((double)resultWidth/originWidth);
			//logger.debug("resultWidth : " + resultWidth);
			//logger.debug("originWidth : " + originWidth);
			//logger.debug("optHeight1 : " + ((double)resultWidth/originWidth));
			//logger.debug("optHeight2 : " + optHeight);
	        if(optHeight>=resultHeight){
	            scaleMode = Scalr.Mode.FIT_TO_WIDTH;
	            maxSize = resultWidth;
	        }
	
	        // Scale the image to given size
	        BufferedImage outputImage = Scalr.resize(inputImage, Scalr.Method.QUALITY, scaleMode, maxSize);
	
	    	BufferedImage scaledImage = Scalr.crop(outputImage, resultWidth, resultHeight);
			ImageIO.write(scaledImage, imageType, new File(targetFilePath));
	
	        // flush both images
	        inputImage.flush();
	        outputImage.flush();
	        scaledImage.flush();
	    		
	    	mapFile = new HashMap<String, Object>();
	    	mapFile.put("fileWidth", scaledImage.getWidth());
			mapFile.put("fileHeight", scaledImage.getHeight());
        }
		
		return mapFile;
    	
    }

}
