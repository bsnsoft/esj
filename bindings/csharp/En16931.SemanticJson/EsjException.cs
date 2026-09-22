using System;

namespace En16931.SemanticJson;

/// <summary>A document, a registry or a rule pack this implementation refuses.</summary>
public class EsjException : Exception
{
    /// <summary>Creates the exception.</summary>
    /// <param name="message">what was refused, in English</param>
    public EsjException(string message) : base(message)
    {
    }

    /// <summary>Creates the exception.</summary>
    /// <param name="message">what was refused, in English</param>
    /// <param name="cause">what raised it</param>
    public EsjException(string message, Exception cause) : base(message, cause)
    {
    }
}
