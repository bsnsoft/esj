-- ESJ Viewer: the application Finder offers under "Open With" for an XML or a PDF file.
-- It runs `esj view --report --lang <de|en> <file>` for each file it is handed, which
-- writes the page and opens it in the browser; the language is the user's. esj is the
-- executable beside this application, else the one Homebrew installed. A run that ends
-- without a page shows what esj wrote instead, because nobody reads the error stream of a
-- program Finder started. dist/viewer-app.sh compiles this into ESJ Viewer.app.
--
-- Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

on open theFiles
	set esj to esjPath()
	set german to isGerman()
	if esj is "" then
		if german then
			set notFound to "esj wurde nicht gefunden, weder neben ESJ Viewer.app noch in /opt/homebrew/bin oder /usr/local/bin."
		else
			set notFound to "esj was not found, neither beside ESJ Viewer.app nor in /opt/homebrew/bin or /usr/local/bin."
		end if
		display dialog notFound buttons {"OK"} default button 1 with title "ESJ Viewer" with icon stop
		return
	end if
	if german then
		set lang to "de"
	else
		set lang to "en"
	end if
	repeat with theFile in theFiles
		set thePath to POSIX path of theFile
		set output to do shell script quoted form of esj & " view --report --lang " & lang & " " & quoted form of thePath & " 2>&1; true"
		if not wrotePage(output) then
			if (length of output) > 1500 then set output to (text 1 thru 1500 of output) & " ..."
			display dialog output buttons {"OK"} default button 1 with title "ESJ Viewer" with icon caution
		end if
	end repeat
end open

-- Started on its own rather than with a file: ask for the files.
on run
	set theFiles to choose file with multiple selections allowed
	open theFiles
end run

-- Whether esj printed the path of a page: a line that is an absolute path to index.html.
on wrotePage(output)
	repeat with theLine in paragraphs of output
		set lineText to theLine as text
		if lineText starts with "/" and lineText ends with "/index.html" then return true
	end repeat
	return false
end wrotePage

-- esj beside this application, else where Homebrew puts it on Apple silicon and on Intel.
on esjPath()
	set appPath to POSIX path of (path to me)
	set nextToApp to (do shell script "dirname " & quoted form of appPath) & "/esj"
	repeat with candidate in {nextToApp, "/opt/homebrew/bin/esj", "/usr/local/bin/esj"}
		try
			do shell script "test -x " & quoted form of (candidate as text)
			return candidate as text
		end try
	end repeat
	return ""
end esjPath

-- Whether the user's locale is German.
on isGerman()
	return (user locale of (system info)) starts with "de"
end isGerman
