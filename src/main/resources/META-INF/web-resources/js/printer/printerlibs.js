// IO Function
function createWebSocketIO() {
	var io = new Object();
	var ws = null;
	
	io.ws = null;
	io.open = function(address, port){
		ws = new WebSocket('ws://' + address + ':' + port);
		io.ws = ws;
		ws.binaryType = "arraybuffer";
		ws.onopen = io.onopen;
		ws.onerror = io.onerror;
		ws.onclose = io.onclose;
		ws.onmessage = io.onmessage;
		return true;
	};
	io.close = function(){
		if (ws != null) {
			ws.close();
		}
	};
	io.isOpened = function(){
		if (ws == null)
			return false;
		if (ws.readyState == 1)
			return true;
		else
			return false;
	};
	io.write = function(data){
		if (!io.isOpened())
			return -1;
			
		ws.send(data);
	};
	io.read = null;
	
	io.onopen = null;
	io.onerror = null;
	io.onclose = null;
	io.onmessage = null;
	return io;
}

// Basic Function
function POS_Reset(io) {
	var data = new Uint8Array([0x1b, 0x40]);
	io.write(data);
	return io.isOpened();
}
function POS_PrintSelfTestPage(io) {
	var data = new Uint8Array([0x12, 0x54]);
	io.write(data);
	return io.isOpened();
}
function POS_SetPrintSpeed(io, nSpeed) {
    var data = new Uint8Array([0x1F, 0x28, 0x73, 0x02, 0x00, (nSpeed & 0xFF), ((nSpeed & 0xFF00) >> 8)]);
    io.write(data);
    return io.isOpened();
}
function POS_FeedAndCutPaper(io) {
    var data = new Uint8Array([0x1d, 0x56, 0x42, 0x00]);
    io.write(data);
    return io.isOpened();
}
function POS_FullCutPaper(io) {
	var data = new Uint8Array([0x1b, 0x69]);
	io.write(data);
	return io.isOpened();
}
function POS_HalfCutPaper(io) {
	var data = new Uint8Array([0x1b, 0x6d]);
	io.write(data);
	return io.isOpened();
}
function POS_Beep(io, nBeepCount, nBeepMillis) {
    var data = new Uint8Array([0x1b, 0x42, nBeepCount, nBeepMillis]);
    io.write(data);
    return io.isOpened();
}
function POS_KickDrawer(io, nDrawerIndex, nHightLevelTime, nLowLevelTime) {
    var data = new Uint8Array([0x1b, 0x70, nDrawerIndex, nHightLevelTime, nLowLevelTime]);
    io.write(data);
    return io.isOpened();
}
function POS_SetDoubleByteEncoding(io, nEncoding) {
	var data = new Uint8Array([0x1b, 0x39, nEncoding]);
	io.write(data);
	return io.isOpened();
}
function POS_SetDoubleByteMode(io) {
	var data = new Uint8Array([0x1c, 0x26]);
	io.write(data);
	return io.isOpened();
}
function POS_SetSingleByteMode(io) {
	var data = new Uint8Array([0x1c, 0x2e]);
	io.write(data);
	return io.isOpened();
}
function POS_SetCharacterSet(io, nCharacterSet) {
	var data = new Uint8Array([0x1b, 0x52, nCharacterSet]);
	io.write(data);
	return io.isOpened();
}
function POS_SetCharacterCodepage(io, nCharacterCodepage) {
	var data = new Uint8Array([0x1b, 0x74, nCharacterCodepage]);
	io.write(data);
	return io.isOpened();
}
function POS_FeedLine(io) {
    var data = new Uint8Array([0x0d, 0x0a]);
	io.write(data);
	return io.isOpened();
}
function POS_FeedLines(io, lines) {
    var data = new Uint8Array([0x1b, 0x64, lines]);
	io.write(data);
	return io.isOpened();
}
function POS_FeedDots(io, dots) {
    var data = new Uint8Array([0x1b, 0x4a, dots]);
	io.write(data);
	return io.isOpened();
}
function POS_SetMotionUnit(io, nHorizontalMU, nVerticalMU) {
    var data = new Uint8Array([0x1d, 0x50, nHorizontalMU, nVerticalMU]);
	io.write(data);
	return io.isOpened();
}
function POS_SetCharRightSpacing(io, nDistance) {
    var data = new Uint8Array([0x1b, 0x20, nDistance]);
	io.write(data);
	return io.isOpened();
}
function POS_SetLineHeight(io, nHeight) {
    var data = new Uint8Array([0x1b, 0x33, nHeight]);
	io.write(data);
	return io.isOpened();
}
function POS_SetHriFontType(io, nHriFontType) {
    var data = new Uint8Array([0x1d, 0x66, (nHriFontType & 0x01)]);
	io.write(data);
	return io.isOpened();
}
function POS_SetHriFontPosition(io, nHriFontPosition) {
    var data = new Uint8Array([0x1d, 0x48, (nHriFontPosition & 0x03)]);
	io.write(data);
	return io.isOpened();
}

// helper functions
function ComputeStringWidth(str, nEngCharWidth, nChnCharWidth)
{
    var nWidth = 0;
    var len,c;  
    len = str.length; 
    for (var i = 0; i < len; ++i) {
    	c = str.charCodeAt(i);
        if (c < 0x20)
            break;
        else if (c < 0x100)
            nWidth += nEngCharWidth;
        else
            nWidth += nChnCharWidth;
    }
    return nWidth;
}
function PageAreaAlignmentToXPosition(io, x, nWidth) {
	// 左对齐
    if (x == -1)
    {
        x = 0;
    }
    // 中对齐
    else if (x == -2)
    {
        if (io.dir == 0 || io.dir == 2)
        {
            x = ((io.r - io.l) - nWidth) / 2;
        }
        else if (io.dir == 1 || io.dir == 3)
        {
            x = ((io.b - io.t) - nWidth) / 2;
        }
    }
    // 右对齐
    else if (x == -3)
    {
        if (io.dir == 0 || io.dir == 2)
        {
            x = (io.r - io.l) - nWidth;
        }
        else if (io.dir == 1 || io.dir == 3)
        {
            x = (io.b - io.t) - nWidth;
        }
    }
    return x;
}
function STR2UTF8(str) {  
    var bytes = new Array();   
    var len,c;  
    len = str.length;  
    for(var i = 0; i < len; i++){  
        c = str.charCodeAt(i);  
        if(c >= 0x010000 && c <= 0x10FFFF){  
            bytes.push(((c >> 18) & 0x07) | 0xF0);  
            bytes.push(((c >> 12) & 0x3F) | 0x80);  
            bytes.push(((c >> 6) & 0x3F) | 0x80);  
            bytes.push((c & 0x3F) | 0x80);  
        }else if(c >= 0x000800 && c <= 0x00FFFF){  
            bytes.push(((c >> 12) & 0x0F) | 0xE0);  
            bytes.push(((c >> 6) & 0x3F) | 0x80);  
            bytes.push((c & 0x3F) | 0x80);  
        }else if(c >= 0x000080 && c <= 0x0007FF){  
            bytes.push(((c >> 6) & 0x1F) | 0xC0);  
            bytes.push((c & 0x3F) | 0x80);  
        }else{  
            bytes.push(c & 0xFF);  
        }  
    }  
    return new Uint8Array(bytes);  
}  
function Uint8ArraysToUint8Array(arrs) {
	var new_len = 0;
	for (var i = 0; i < arrs.length; ++i) {
		var arr = arrs[i];
		new_len += arr.length;
	}
	var new_arr = new Uint8Array(new_len);
	var new_idx = 0;
	for (var i = 0; i < arrs.length; ++i) {
		var arr = arrs[i];
		for (var j = 0; j < arr.length; ++j) {
			new_arr[new_idx++] = arr[j];
		}
	} 
	return new_arr;
}
function Image1PixOffset(w, x, y) {
    return y*w + x;
}
function Image1ToRasterCmd(width, height, src)
{
	var x = parseInt((width + 7) / 8);
	var y = parseInt(height);
	var dstlen = 8 + x * y;
	
	var dst = new Uint8Array(dstlen);
	dst[0] = 0x1d;
	dst[1] = 0x76;
	dst[2] = 0x30;
	dst[3] = 0x00;
	dst[4] = (x % 256);
    dst[5] = (x / 256);
    dst[6] = (y % 256);
    dst[7] = (y / 256);

    var dst_idx = 8;
    var d = 0;
    for (var j = 0; j < height; ++j)
    {
        for (var i = 0; i < width; ++i)
        {
            var offset = Image1PixOffset(width, i, j);
            if (i % 8 == 0)
            {
                d = ((src[offset] ? 0x01 : 0x00) << (7 - (i % 8)));
            }
            else
            {
                d |= ((src[offset] ? 0x01 : 0x00) << (7 - (i % 8)));
            }

            if ((i % 8 == 7) || (i == width - 1))
            {
                dst[dst_idx++] = d;
            }
        }
    }
    
	return dst;
}
function Image1ToRasterData(width, height, src)
{
	var x = parseInt((width + 7) / 8);
	var y = parseInt(height);
	var dstlen = x * y;
	
	var dst = new Uint8Array(dstlen);

    var dst_idx = 0;
    var d = 0;
    for (var j = 0; j < height; ++j)
    {
        for (var i = 0; i < width; ++i)
        {
            var offset = Image1PixOffset(width, i, j);
            if (i % 8 == 0)
            {
                d = ((src[offset] ? 0x01 : 0x00) << (7 - (i % 8)));
            }
            else
            {
                d |= ((src[offset] ? 0x01 : 0x00) << (7 - (i % 8)));
            }

            if ((i % 8 == 7) || (i == width - 1))
            {
                dst[dst_idx++] = d;
            }
        }
    }
    
	return dst;
}
function Image1ToTM88IVRasterCmd(width, height, src) {
    var x = parseInt((width + 7) / 8) * 8;
    var y = parseInt((height + 7) / 8) * 8;
    var dstlen = 7 + 10 + 7 + 2 + x * y / 8;

    var dst = new Uint8Array(dstlen);
    dst[0] = 0x1d;
    dst[1] = 0x38;
    dst[2] = 0x4C;
    dst[3] = ((x*y / 8 + 10) & 0xFF);
    dst[4] = (((x*y / 8 + 10) >> 8) & 0xFF);
    dst[5] = (((x*y / 8 + 10) >> 16) & 0xFF);
    dst[6] = (((x*y / 8 + 10) >> 24) & 0xFF);

    dst[7] = 0x30; dst[8] = 0x70; dst[9] = 0x30; dst[10] = 0x01; dst[11] = 0x01; dst[12] = 0x31;
    dst[13] = (x % 256);
    dst[14] = (x / 256);
    dst[15] = (y % 256);
    dst[16] = (y / 256);

    var cmdPrint = new Uint8Array([0x1d, 0x38, 0x4c, 0x02, 0x00, 0x00, 0x00, 0x30, 0x02]);
    for (var i = dstlen - cmdPrint.length, j = 0; i < dstlen; ++i,++j) {
    	dst[i] = cmdPrint[j];
    }

	var dst_idx = 17;
	var d = 0;
    for (var j = 0; j < height; ++j)
    {
        for (var i = 0; i < width; ++i)
        {
            var offset = Image1PixOffset(width, i, j);
            if (i % 8 == 0)
            { 
                d = ((src[offset] ? 0x01 : 0x00) << (7 - (i % 8)));
            }
            else
            {
                d |= ((src[offset] ? 0x01 : 0x00) << (7 - (i % 8)));
            }

            if ((i % 8 == 7) || (i == width - 1))
            {
                dst[dst_idx++] = d;
            }
        }
    }
    
	return dst;
}
function ImageRGBAToImageGray(width, height, src, dst) {
	var pixel_count = width * height;
	var src_index = 0;
	var dst_index = 0;
	
	for (var pixel_index = 0; pixel_index < pixel_count; ++pixel_index) {
		var r = src[src_index + 0];
		var g = src[src_index + 1];
		var b = src[src_index + 2];
		var a = src[src_index + 3];
		if (a == 0)
			dst[dst_index] = 255;
		else
			dst[dst_index] = parseInt((r * 0.299 + g * 0.587 + b * 0.114));
		src_index += 4;
		dst_index += 1;
	}
}
function ImageGrayToImage1_Threshold(width, height, src, dst) {
    var pixel_count = width * height;
    
    var graytotal = 0;
	for (var pixel_index = 0; pixel_index < pixel_count; ++pixel_index) {
		graytotal += src[pixel_index];
	}
    var grayave = graytotal / pixel_count;

    /* 二值化 */
	for (var pixel_index = 0; pixel_index < pixel_count; ++pixel_index) {
		dst[pixel_index] = src[pixel_index] > grayave ? 0 : 1;
	}
}
var Floyd16x16 = [
    [ 0, 128, 32, 160, 8, 136, 40, 168, 2, 130, 34, 162, 10, 138, 42, 170 ],
    [ 192, 64, 224, 96, 200, 72, 232, 104, 194, 66, 226, 98, 202, 74, 234, 106 ],
    [ 48, 176, 16, 144, 56, 184, 24, 152, 50, 178, 18, 146, 58, 186, 26, 154 ],
    [ 240, 112, 208, 80, 248, 120, 216, 88, 242, 114, 210, 82, 250, 122, 218, 90 ],
    [ 12, 140, 44, 172, 4, 132, 36, 164, 14, 142, 46, 174, 6, 134, 38, 166 ],
    [ 204, 76, 236, 108, 196, 68, 228, 100, 206, 78, 238, 110, 198, 70, 230, 102 ],
    [ 60, 188, 28, 156, 52, 180, 20, 148, 62, 190, 30, 158, 54, 182, 22, 150 ],
    [ 252, 124, 220, 92, 244, 116, 212, 84, 254, 126, 222, 94, 246, 118, 214, 86 ],
    [ 3, 131, 35, 163, 11, 139, 43, 171, 1, 129, 33, 161, 9, 137, 41, 169 ],
    [ 195, 67, 227, 99, 203, 75, 235, 107, 193, 65, 225, 97, 201, 73, 233, 105 ],
    [ 51, 179, 19, 147, 59, 187, 27, 155, 49, 177, 17, 145, 57, 185, 25, 153 ],
    [ 243, 115, 211, 83, 251, 123, 219, 91, 241, 113, 209, 81, 249, 121, 217, 89 ],
    [ 15, 143, 47, 175, 7, 135, 39, 167, 13, 141, 45, 173, 5, 133, 37, 165 ],
    [ 207, 79, 239, 111, 199, 71, 231, 103, 205, 77, 237, 109, 197, 69, 229, 101 ],
    [ 63, 191, 31, 159, 55, 183, 23, 151, 61, 189, 29, 157, 53, 181, 21, 149 ],
    [ 254, 127, 223, 95, 247, 119, 215, 87, 253, 125, 221, 93, 245, 117, 213, 85 ] ];
function ImageGrayToImage1_Dithering(width, height, src, dst) {
	var pixel_index = 0;
	for (var y = 0; y < height; ++y) {
		for (var x = 0; x < width; ++x) {
			dst[pixel_index++] = src[pixel_index] > Floyd16x16[x & 15][y & 15] ? 0 : 1;
		}
	}
}

// text barcode qrcode image
function POS_TextOut(io, str, x, nWidthScale, nHeightScale, nFontType, nFontStyle) {
	
	// alignment
	var ESC_a_n = new Uint8Array([0x1b, 0x61, 0x00]);

	// x position
	var ESC_dollors_nL_nH = new Uint8Array([0x1b, 0x24, 0x00, 0x00]);

	if (x == -1)
	{
		ESC_a_n[2] = 0x00;
		ESC_dollors_nL_nH[2] = 0;
		ESC_dollors_nL_nH[3] = 0;
	}
	else if (x == -2)
	{
		ESC_a_n[2] = 0x01;
		ESC_dollors_nL_nH[2] = 0;
		ESC_dollors_nL_nH[3] = 0;
	}
	else if (x == -3)
	{
		ESC_a_n[2] = 0x02;
		ESC_dollors_nL_nH[2] = 0;
		ESC_dollors_nL_nH[3] = 0;
	}
	else
	{
		ESC_a_n[2] = 0x00;
		ESC_dollors_nL_nH[2] = (x % 0x100);
		ESC_dollors_nL_nH[3] = (x / 0x100);
	}
 
	// scale
	var GS_exclamationmark_n = new Uint8Array([0x1d, 0x21, (nHeightScale | (nWidthScale << 4))]);

	// fonttype
	var ESC_M_n = new Uint8Array([0x1b, 0x4d, nFontType]);

	// bold
	var ESC_E_n = new Uint8Array([0x1b, 0x45, ((nFontStyle >> 3) & 0x01)]);

	// underline
	var ESC_line_n = new Uint8Array([0x1b, 0x2d, ((nFontStyle >> 7) & 0x03)]);
	var FS_line_n = new Uint8Array([0x1c, 0x2d, ((nFontStyle >> 7) & 0x03)]);

	// upside down
	var ESC_lbracket_n = new Uint8Array([0x1b, 0x7b, ((nFontStyle >> 9) & 0x01)]);

	// black white reverse display
	var GS_B_n = new Uint8Array([0x1d, 0x42, ((nFontStyle >> 10) & 0x01)]);

	// rotate 90
	var ESC_V_n = new Uint8Array([0x1b, 0x56, ((nFontStyle >> 12) & 0x01)]);
 
 	var CMD_UTF8 = new Uint8Array([0x1c, 0x26, 0x1b, 0x39, 0x01]);
 	
 	var STR_UTF8_array = STR2UTF8(str);
 	 
 	var data = Uint8ArraysToUint8Array(new Array(
 		ESC_a_n,
		ESC_dollors_nL_nH,
		GS_exclamationmark_n,
		ESC_M_n,
		ESC_E_n,
		ESC_line_n,
		FS_line_n,
		ESC_lbracket_n,
		GS_B_n,
		ESC_V_n,
		CMD_UTF8,
		STR_UTF8_array
 	));
	 
	io.write(data);
	return io.isOpened();
}
function POS_SetBarcode(io, str, x, nBarcodeUnitWidth, nBarcodeHeight, nHriFontType, nHriFontPosition, nBarcodeType) {
    
    // alignment
    var ESC_a_n = new Uint8Array([0x1b, 0x61, 0x00]);

    // x position
    var ESC_dollors_nL_nH = new Uint8Array([0x1b, 0x24, 0x00, 0x00]);

    if(x == -1)
    {
        ESC_a_n[2] = 0x00;
        ESC_dollors_nL_nH[2] = 0;
        ESC_dollors_nL_nH[3] = 0;
    }
    else if (x == -2)
    {
        ESC_a_n[2] = 0x01;
        ESC_dollors_nL_nH[2] = 0;
        ESC_dollors_nL_nH[3] = 0;
    }
    else if (x == -3)
    {
        ESC_a_n[2] = 0x02;
        ESC_dollors_nL_nH[2] = 0;
        ESC_dollors_nL_nH[3] = 0;
    }
    else
    {
        ESC_a_n[2] = 0x00;
        ESC_dollors_nL_nH[2] = (x%0x100);
        ESC_dollors_nL_nH[3] = (x/0x100);
    }

    var GS_w_n = new Uint8Array([0x1d, 0x77, nBarcodeUnitWidth]);
    var GS_h_n = new Uint8Array([0x1d, 0x68, nBarcodeHeight]);
    var GS_f_n = new Uint8Array([0x1d, 0x66, (nHriFontType & 0x01)]);
    var GS_H_n = new Uint8Array([0x1d, 0x48, (nHriFontPosition & 0x03)]);
    
    var STR_UTF8_array = STR2UTF8(str);
    var GS_k_m_n_ = new Uint8Array([0x1d, 0x6b, nBarcodeType, STR_UTF8_array.length]);

	var data = Uint8ArraysToUint8Array(new Array(
 		ESC_a_n,
		ESC_dollors_nL_nH,
		GS_w_n,
		GS_h_n,
		GS_f_n,
		GS_H_n,
		GS_k_m_n_,
		STR_UTF8_array
 	));
	 
	io.write(data);
	return io.isOpened();
}
function POS_PrintQRcode(io, str, x, nUnitWidth, nVersion, nECCLevel) {

    // alignment
    var ESC_a_n = new Uint8Array([0x1b, 0x61, 0x00]);

    // x position
    var ESC_dollors_nL_nH = new Uint8Array([0x1b, 0x24, 0x00, 0x00]);

    if(x == -1)
    {
        ESC_a_n[2] = 0x00;
        ESC_dollors_nL_nH[2] = 0;
        ESC_dollors_nL_nH[3] = 0;
    }
    else if (x == -2)
    {
        ESC_a_n[2] = 0x01;
        ESC_dollors_nL_nH[2] = 0;
        ESC_dollors_nL_nH[3] = 0;
    }
    else if (x == -3)
    {
        ESC_a_n[2] = 0x02;
        ESC_dollors_nL_nH[2] = 0;
        ESC_dollors_nL_nH[3] = 0;
    }
    else
    {
        ESC_a_n[2] = 0x00;
        ESC_dollors_nL_nH[2] = (x%0x100);
        ESC_dollors_nL_nH[3] = (x/0x100);
    }

    var GS_w_n = new Uint8Array([0x1d, 0x77, nUnitWidth]);
    var STR_UTF8_array = STR2UTF8(str);
    var GS_k_m_v_r_nL_nH = new Uint8Array([0x1d, 0x6b, 0x61, nVersion, nECCLevel, STR_UTF8_array.length, (STR_UTF8_array.length >> 8)]);

	var data = Uint8ArraysToUint8Array(new Array(
 		ESC_a_n,
		ESC_dollors_nL_nH,
		GS_w_n,
		GS_k_m_v_r_nL_nH,
		STR_UTF8_array
 	));
	 
	io.write(data);
	return io.isOpened();
}
function POS_PrintImage1(io, img, w, h, x) {
	
    // alignment
    var ESC_a_n = new Uint8Array([0x1b, 0x61, 0x00]);

    // x position
    var ESC_dollors_nL_nH = new Uint8Array([0x1b, 0x24, 0x00, 0x00]);

    if(x == -1)
    {
        ESC_a_n[2] = 0x00;
        ESC_dollors_nL_nH[2] = 0;
        ESC_dollors_nL_nH[3] = 0;
    }
    else if (x == -2)
    {
        ESC_a_n[2] = 0x01;
        ESC_dollors_nL_nH[2] = 0;
        ESC_dollors_nL_nH[3] = 0;
    }
    else if (x == -3)
    {
        ESC_a_n[2] = 0x02;
        ESC_dollors_nL_nH[2] = 0;
        ESC_dollors_nL_nH[3] = 0;
    }
    else
    {
        ESC_a_n[2] = 0x00;
        ESC_dollors_nL_nH[2] = (x%0x100);
        ESC_dollors_nL_nH[3] = (x/0x100);
    }
    
    var IMG_Array = Image1ToRasterCmd(w, h, img);
    
    var data = Uint8ArraysToUint8Array(new Array(
 		ESC_a_n,
		ESC_dollors_nL_nH,
		IMG_Array
 	));
	 
	io.write(data);
	return io.isOpened();
}
function POS_PrintImageRGBA(io, img, w, h, x, nBinaryAlgorithm) {
	if ((w <= 0) || (h <= 0))
		return false;
		
	var imggray = new Uint8Array(w * h);
	ImageRGBAToImageGray(w, h, img, imggray);
	var img1 = new Uint8Array(w * h);
	switch (nBinaryAlgorithm) {
	case 0:
		ImageGrayToImage1_Dithering(w, h, imggray, img1);
		break;
	case 1:
	default:
		ImageGrayToImage1_Threshold(w, h, imggray, img1);
		break;
	}
	return POS_PrintImage1(io, img1, w, h, x);
}

// Page Function
function PAGE_PageEnter(io) {
	var lineheight = 32;
    var data = new Uint8Array([0x1d, 0x50, 0xc8, 0xc8, 0x1b, 0x4c, 0x1b, 0x33, lineheight]);
    io.write(data);
	return io.isOpened();
}
function PAGE_PagePrint(io) {
    var data = new Uint8Array([0x1b, 0x0c]);
    io.write(data);
	return io.isOpened();
}
function PAGE_PageClear(io) {
    var data = new Uint8Array([0x18]);
    io.write(data);
	return io.isOpened();
}
function PAGE_PageExit(io) {
    var data = new Uint8Array([0x1b, 0x53]);
    io.write(data);
	return io.isOpened();
}
function PAGE_SetPrintArea(io, left, top, right, bottom, direction) {
    var data = new Uint8Array([0x1b, 0x57, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1b, 0x54, 0x00]);
    
    data[2] = left;
    data[3] = (left >> 8);
    data[4] = top;
    data[5] = (top >> 8);
    data[6] = (right - left);
    data[7] = ((right - left) >> 8);
    data[8] = (bottom - top);
    data[9] = ((bottom - top) >> 8);

    data[12] = direction;

	io.l = left;
    io.t = top;
    io.r = right;
    io.b = bottom;
    io.dir = direction;

    io.write(data);
	return io.isOpened();
}
function PAGE_DrawString(io, pszString, x, y, nWidthScale, nHeightScale, nFontType, nFontStyle) {
    
    if (x < 0)
    {
    	var nWidth = ComputeStringWidth(pszString, 12 * (nWidthScale + 1), 24 * (nWidthScale + 1));
    	x = PageAreaAlignmentToXPosition(io, x, nWidth);
    }
    if (y >= 0)
    {
    	var baseline = 21;
        y += 24 * (1 + nHeightScale) - baseline;
    }

    var bufx = new Uint8Array([0x1b, 0x24, (x), (x >> 8)]);
    var bufy = new Uint8Array([0x1d, 0x24, (y), (y >> 8)]);
    var buffont = new Uint8Array([0x1b, 0x21, (nFontType)]);
    var bufscale = new Uint8Array([0x1d, 0x21, (nWidthScale << 4 | nHeightScale)]);
    var bufbold = new Uint8Array([0x1b, 0x45, (nFontStyle & 0x08 ? 1 : 0)]);
    // underline
	var ESC_line_n = new Uint8Array([0x1b, 0x2d, ((nFontStyle >> 7) & 0x03)]);
	var FS_line_n = new Uint8Array([0x1c, 0x2d, ((nFontStyle >> 7) & 0x03)]);
    var bufreverse = new Uint8Array([0x1b, 0x7b, (nFontStyle & 0x200 ? 1 : 0)]);
    var bufhighlight = new Uint8Array([0x1d, 0x42, (nFontStyle & 0x400 ? 1 : 0)]);

	var CMD_UTF8 = new Uint8Array([0x1c, 0x26, 0x1b, 0x39, 0x01]);
    var STR_UTF8_array = STR2UTF8(pszString);
 	 
 	var data = Uint8ArraysToUint8Array(new Array(
 		bufx,
		bufy,
		buffont,
		bufscale,
		bufbold,
		ESC_line_n, 
		FS_line_n,
		bufreverse,
		bufhighlight,
		CMD_UTF8,
		STR_UTF8_array
 	));
	 
	io.write(data);
	return io.isOpened();
}
function PAGE_DrawRect(io, x, y, nWidth, nHeight, nColor) {
    var data = new Uint8Array([0x1F, 0x28, 0x52, 0x0a, 0x00, (x >> 8), (x), (y >> 8), (y), ((x + nWidth - 1) >> 8), (x + nWidth - 1), ((y + nHeight - 1) >> 8), (y + nHeight - 1), (nColor), 0x01]);
    io.write(data);
	return io.isOpened();
}
function PAGE_DrawBarcode(io, pszContent, x, y, nBarcodeUnitWidth, nBarcodeHeight, nHriFontType, nHriFontPosition, nBarcodeType) {
    if (y >= 0)
    {
    	var baseline = 21;
        var nHriFontHeight = nHriFontType == 0 ? 24 : 17;
        var nHriCharPrint = parseInt(Math.ceil((nHriFontPosition & 0x3) / 2.0));
        y += nHriFontHeight*nHriCharPrint + nBarcodeHeight - baseline;
    }

    var bufx = new Uint8Array([0x1b, 0x24, (x), (x >> 8)]);
    var bufy = new Uint8Array([0x1d, 0x24, (y), (y >> 8)]);
    var head = new Uint8Array([
    	0x1d, 0x77, 0x02,
        0x1d, 0x68, 0xa2,
        0x1d, 0x66, 0x00,
        0x1d, 0x48, 0x02,
        0x1d, 0x6b, 0x00, 0x00,]);

	var STR_UTF8_array = STR2UTF8(pszContent);
    var szlen = STR_UTF8_array.length;

    head[2] = nBarcodeUnitWidth;
    head[5] = nBarcodeHeight;
    head[8] = (nHriFontType & 0x1);
    head[11] = (nHriFontPosition & 0x3);
    head[14] = (nBarcodeType);
    head[15] = szlen;

    var data = Uint8ArraysToUint8Array(new Array(
 		bufx,
		bufy,
		head,
		STR_UTF8_array
 	));
	 
	io.write(data);
	return io.isOpened();
}
function PAGE_DrawQRCode(io, pszContent, x, y, nQRCodeUnitWidth, nVersion, nEcLevel) {
    if (y >= 0)
    {
    	var lineheight = 32;
        y += lineheight;
    }

    var bufx = new Uint8Array([0x1b, 0x24, (x), (x >> 8)]);
    var bufy = new Uint8Array([0x1d, 0x24, (y), (y >> 8)]);

    var head = new Uint8Array([
    	0x1d, 0x77, 0x02,
        0x1d, 0x6b, 0x61, 0x0a, 0x01, 0x00, 0x00,
    ]);

    var STR_UTF8_array = STR2UTF8(pszContent);
    var szlen = STR_UTF8_array.length;

    head[2] = nQRCodeUnitWidth;

    head[6] = nVersion;
    head[7] = nEcLevel;
    head[8] = (szlen);
    head[9] = (szlen >> 8);

    var data = Uint8ArraysToUint8Array(new Array(
 		bufx,
		bufy,
		head,
		STR_UTF8_array
 	));
	 
	io.write(data);
	return io.isOpened();
}
function PAGE_DrawImage1(io, img, w, h, x, y) {
	if (x < 0)
    {
    	x = PageAreaAlignmentToXPosition(io, x, w);
    }
    if (y >= 0)
    {
    	var baseline = 21;
        y += h - baseline;
    }

    var bufx = new Uint8Array([0x1b, 0x24, (x), (x >> 8)]);
    var bufy = new Uint8Array([0x1d, 0x24, (y), (y >> 8)]);

	var IMG_Array = Image1ToTM88IVRasterCmd(w, h, img);
     
    var data = Uint8ArraysToUint8Array(new Array(
 		bufx,
		bufy,
		IMG_Array
 	));
	 
	io.write(data);
	return io.isOpened();
}
function PAGE_DrawImageRGBA(io, img, w, h, x, y, nBinaryAlgorithm) {
    if ((w <= 0) || (h <= 0))
		return false;
		 
	var imggray = new Uint8Array(w * h);
	ImageRGBAToImageGray(w, h, img, imggray);
	var img1 = new Uint8Array(w * h);
	switch (nBinaryAlgorithm) {
	case 0:
		ImageGrayToImage1_Dithering(w, h, imggray, img1);
		break;
	case 1:
	default:
		ImageGrayToImage1_Threshold(w, h, imggray, img1);
		break; 
	}
	return PAGE_DrawImage1(io, img1, w, h, x, y);
}

// Label Function
function LABEL_PageBegin(io, startx, starty, width, height, rotate) {
	// Set Codepage To UTF8
	var CMD_UTF8 = new Uint8Array([0x1c, 0x26, 0x1b, 0x39, 0x01]);
	io.write(CMD_UTF8);
	
    var data = new Uint8Array(12);

    data[0] = 0x1A;
    data[1] = 0x5B;
    data[2] = 0x01;

    data[3] =  (startx & 0xFF);
    data[4] =  ((startx >> 8) & 0xFF);
    data[5] =  (starty & 0xFF);
    data[6] =  ((starty >> 8) & 0xFF);

    data[7] =  (width & 0xFF);
    data[8] =  ((width >> 8) & 0xFF);
    data[9] =  (height & 0xFF);
    data[10] =  ((height >> 8) & 0xFF);

    data[11] =  (rotate & 0xFF);

    io.write(data);
	return io.isOpened();
}
function LABEL_PageEnd(io) {
    var data = new Uint8Array([0x1A, 0x5D, 0x00]);
    io.write(data);
	return io.isOpened();
}
function LABEL_PagePrint(io, num) {
    var data = new Uint8Array([0x1A, 0x4F, 0x01, num]);
    io.write(data);
	return io.isOpened();
}
function LABEL_PageFeed(io) {
    var data = new Uint8Array([0x1A, 0x0C, 0x00]);
    io.write(data);
	return io.isOpened();
}
function LABEL_PageCalibrate(io) {
    var data = new Uint8Array([0x1f, 0x63]);
    io.write(data);
	return io.isOpened();
}
function LABEL_DrawLine(io, startx, starty, endx, endy, width, color) {
    var data = new Uint8Array(14);

    data[0] = 0x1A;
    data[1] = 0x5C;
    data[2] = 0x01;

    data[3] =  (startx & 0xFF);
    data[4] =  ((startx >> 8) & 0xFF);
    data[5] =  (starty & 0xFF);
    data[6] =  ((starty >> 8) & 0xFF);

    data[7] =  (endx & 0xFF);
    data[8] =  ((endx >> 8) & 0xFF);
    data[9] =  (endy & 0xFF);
    data[10] =  ((endy >> 8) & 0xFF);

    data[11] =  (width & 0xFF);
    data[12] =  ((width >> 8) & 0xFF);

    data[13] =  (color & 0xFF);

    io.write(data);
	return io.isOpened();
}
function LABEL_DrawBox(io, left, top, right, bottom, borderwidth, bordercolor) {
    var data = new Uint8Array(14);

    data[0] = 0x1A;
    data[1] = 0x26;
    data[2] = 0x01;

    data[3] =  (left & 0xFF);
    data[4] =  ((left >> 8) & 0xFF);
    data[5] =  (top & 0xFF);
    data[6] =  ((top >> 8) & 0xFF);

    data[7] =  (right & 0xFF);
    data[8] =  ((right >> 8) & 0xFF);
    data[9] =  (bottom & 0xFF);
    data[10] =  ((bottom >> 8) & 0xFF);

    data[11] =  (borderwidth & 0xFF);
    data[12] =  ((borderwidth >> 8) & 0xFF);

    data[13] =  (bordercolor & 0xFF);

    io.write(data);
	return io.isOpened();
}
function LABEL_DrawRectangel(io, left, top, right, bottom, color) {
    var data = new Uint8Array(12);

    data[0] = 0x1A;
    data[1] = 0x2A;
    data[2] = 0x00;

    data[3] =  (left & 0xFF);
    data[4] =  ((left >> 8) & 0xFF);
    data[5] =  (top & 0xFF);
    data[6] =  ((top >> 8) & 0xFF);

    data[7] =  (right & 0xFF);
    data[8] =  ((right >> 8) & 0xFF);
    data[9] =  (bottom & 0xFF);
    data[10] =  ((bottom >> 8) & 0xFF);

    data[11] =  (color & 0xFF);

    io.write(data);
	return io.isOpened();
}
function LABEL_DrawPlainText(io, startx, starty, font, style, str) {
	var CMD_HEAD = new Uint8Array(11);
		CMD_HEAD[0] = 0x1A;
	    CMD_HEAD[1] = 0x54;
	    CMD_HEAD[2] = 0x01;
	
	    CMD_HEAD[3] =  (startx & 0xFF);
	    CMD_HEAD[4] =  ((startx >> 8) & 0xFF);
	    CMD_HEAD[5] =  (starty & 0xFF);
	    CMD_HEAD[6] =  ((starty >> 8) & 0xFF);
	
	    CMD_HEAD[7] =  (font & 0xFF);
	    CMD_HEAD[8] =  ((font >> 8) & 0xFF);
	    CMD_HEAD[9] =  (style & 0xFF);
	    CMD_HEAD[10] =  ((style >> 8) & 0xFF);

    var STR_UTF8_array = STR2UTF8(str);
    var STR_TAIL = new Uint8Array([0]);
 	 
 	var data = Uint8ArraysToUint8Array(new Array(
 		CMD_HEAD,
		STR_UTF8_array,
		STR_TAIL
 	));
	 
	io.write(data);
	return io.isOpened();
}
function LABEL_DrawBarcode(io, startx, starty, type, height, unitwidth, rotate, str) {
    var CMD_HEAD = new Uint8Array(11);
	    CMD_HEAD[0] = 0x1A;
	    CMD_HEAD[1] = 0x30;
	    CMD_HEAD[2] = 0x00;
	
	    CMD_HEAD[3] =  (startx & 0xFF);
	    CMD_HEAD[4] =  ((startx >> 8) & 0xFF);
	    CMD_HEAD[5] =  (starty & 0xFF);
	    CMD_HEAD[6] =  ((starty >> 8) & 0xFF);
	
	    CMD_HEAD[7] =  (type & 0xFF);
	    CMD_HEAD[8] =  (height & 0xFF);
	    CMD_HEAD[9] =  (unitwidth & 0xFF);
	    CMD_HEAD[10] =  (rotate & 0xFF);

    var STR_UTF8_array = STR2UTF8(str);
    var STR_TAIL = new Uint8Array([0]);
 	 
 	var data = Uint8ArraysToUint8Array(new Array(
 		CMD_HEAD,
		STR_UTF8_array,
		STR_TAIL
 	));
	 
	io.write(data);
	return io.isOpened();
}
function LABEL_DrawQRCode(io, startx, starty, version, ecc, unitwidth, rotate, str) {
	var CMD_HEAD = new Uint8Array(11);
        CMD_HEAD[0] = 0x1A;
        CMD_HEAD[1] = 0x31;
        CMD_HEAD[2] = 0x00;

        CMD_HEAD[3] =  (version & 0xFF);
        CMD_HEAD[4] =  (ecc & 0xFF);

        CMD_HEAD[5] =  (startx & 0xFF);
        CMD_HEAD[6] =  ((startx >> 8) & 0xFF);
        CMD_HEAD[7] =  (starty & 0xFF);
        CMD_HEAD[8] =  ((starty >> 8) & 0xFF);

        CMD_HEAD[9] =  (unitwidth & 0xFF);
        CMD_HEAD[10] =  (rotate & 0xFF);

    var STR_UTF8_array = STR2UTF8(str);
    var STR_TAIL = new Uint8Array([0]);
 	 
 	var data = Uint8ArraysToUint8Array(new Array(
 		CMD_HEAD,
		STR_UTF8_array,
		STR_TAIL
 	));
	 
	io.write(data);
	return io.isOpened();
}
function LABEL_DrawPDF417(io, startx, starty, colnum, lwratio, ecc, unitwidth, rotate, str) {
	var CMD_HEAD = new Uint8Array(12);
	    CMD_HEAD[0] = 0x1A;
        CMD_HEAD[1] = 0x31;
        CMD_HEAD[2] = 0x01;

        CMD_HEAD[3] =  (colnum & 0xFF);
        CMD_HEAD[4] =  (ecc & 0xFF);
        CMD_HEAD[5] =  (lwratio & 0xFF);

        CMD_HEAD[6] =  (startx & 0xFF);
        CMD_HEAD[7] =  ((startx >> 8) & 0xFF);
        CMD_HEAD[8] =  (starty & 0xFF);
        CMD_HEAD[9] =  ((starty >> 8) & 0xFF);

        CMD_HEAD[10] =  (unitwidth & 0xFF);
        CMD_HEAD[11] =  (rotate & 0xFF);

    var STR_UTF8_array = STR2UTF8(str);
    var STR_TAIL = new Uint8Array([0]);
 	 
 	var data = Uint8ArraysToUint8Array(new Array(
 		CMD_HEAD,
		STR_UTF8_array,
		STR_TAIL
 	));
	 
	io.write(data);
	return io.isOpened();
}
function LABEL_DrawImage1(io, startx, starty, width, height, style, img)
{
	var CMD_HEAD = new Uint8Array(13);
		CMD_HEAD[0] = 0x1A;
        CMD_HEAD[1] = 0x21;
        CMD_HEAD[2] = 0x01;

        CMD_HEAD[3] =  (startx & 0xFF);
        CMD_HEAD[4] =  ((startx >> 8) & 0xFF);
        CMD_HEAD[5] =  (starty & 0xFF);
        CMD_HEAD[6] =  ((starty >> 8) & 0xFF);

        CMD_HEAD[7] =  (width & 0xFF);
        CMD_HEAD[8] =  ((width >> 8) & 0xFF);
        CMD_HEAD[9] =  (height & 0xFF);
        CMD_HEAD[10] =  ((height >> 8) & 0xFF);

        CMD_HEAD[11] =  (style & 0xFF);
        CMD_HEAD[12] =  ((style >> 8) & 0xFF);

    var IMG_DATA = Image1ToRasterData(width, height, img);
 	 
 	var data = Uint8ArraysToUint8Array(new Array(
 		CMD_HEAD,
		IMG_DATA
 	));
	 
	io.write(data);
	return io.isOpened();
}
function LABEL_DrawImageRGBA(io, startx, starty, width, height, style, img, nBinaryAlgorithm) {
    if ((width <= 0) || (height <= 0))
		return false;
		 
	var imggray = new Uint8Array(width * height);
	ImageRGBAToImageGray(width, height, img, imggray);
	var img1 = new Uint8Array(width * height);
	switch (nBinaryAlgorithm) {
	case 0:
		ImageGrayToImage1_Dithering(width, height, imggray, img1);
		break;
	case 1:
	default:
		ImageGrayToImage1_Threshold(width, height, imggray, img1);
		break; 
	}
	return LABEL_DrawImage1(io, startx, starty, width, height, style, img1);
} 

// TODO
// namespace exception readstatus alignment document