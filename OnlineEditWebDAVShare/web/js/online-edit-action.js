(function() {
	
	YAHOO.Bubbling.fire("registerAction", {
		actionName: "onActionEditOnline",
		fn: function fn_onActionOnlineEdit(record) {
			
			var nodeRefParts = record.nodeRef.split("/");
			var uuid = nodeRefParts[3];
			var fileNameParts = record.fileName.split(".");
			var extension = fileNameParts[1];

			Alfresco.util.Ajax.jsonRequest({
				url: Alfresco.constants.PROXY_URI + "online/webdav/auth/get_ticket",
				successCallback:
				{
					scope: this,
					fn: function getTicketSuccess_success(response) {
						var ticket = response.json.ticket;

						var showDoc = true;
						
						var agent = navigator.userAgent.toLowerCase();
						var urlPrefix = window.location.protocol + "//" + window.location.host + "/alfresco/webdav2/";

						var fullUrl = urlPrefix + ticket + "/" + uuid + "." + extension;

						// if the link represents an Office document and we are in IE try and
						// open the file directly to get WebDAV editing capabilities
						if (agent.indexOf("msie") != -1)
						{
							if (extension.indexOf("doc") != -1 || extension.indexOf("docx") != -1 ||
									extension.indexOf("xls") != -1 || extension.indexOf("xlsx") != -1 ||
									extension.indexOf("ppt") != -1 || extension.indexOf("pptx") != -1 ||
									extension.indexOf("dot") != -1 || extension.indexOf("dotx") != -1)
							{
								try
								{
									var wordDoc = new ActiveXObject("SharePoint.OpenDocuments.1");
									if (wordDoc)
									{
										showDoc = false;
										wordDoc.EditDocument(fullUrl);
									}
								}
								catch(e)
								{
									showDoc = true;
								}
							}
						}

						if (showDoc == true) {
							window.open(fullUrl, "_blank");
						}	
					}
				}
			});
		}
	});
	
})();